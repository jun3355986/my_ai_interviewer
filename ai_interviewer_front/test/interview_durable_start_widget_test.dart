import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/api/interview_api.dart';
import 'package:ai_interviewer_front/api/resume_api.dart';
import 'package:ai_interviewer_front/interview_chat_page.dart';
import 'package:ai_interviewer_front/models/interview_history.dart';
import 'package:ai_interviewer_front/services/interview_service.dart';
import 'package:ai_interviewer_front/services/pending_start_store.dart';
import 'package:ai_interviewer_front/services/resume_service.dart';
import 'package:ai_interviewer_front/upload_resume_page.dart';

void main() {
  testWidgets(
    'skip waits for durable opening attempt then permits exit without cancel',
    (tester) async {
      final api = _DurableStartApi()..startGate = Completer<void>();
      addTearDown(api.dispose);
      final service = InterviewService(
        api,
        turnIdFactory: () => 'opening-1',
        pendingStartStore: _MemoryPendingStartStore(),
        reconnectDelays: const [],
      );

      await _pumpFlow(tester, service);
      await tester.tap(find.byKey(const Key('skip-resume-start')));
      await tester.pump();

      expect(api.startCalls, 1);
      expect(find.byType(InterviewChatPage), findsNothing);
      expect(find.text('正在创建面试...'), findsWidgets);

      api.startGate!.complete();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 350));

      expect(find.byType(InterviewChatPage), findsOneWidget);
      expect(find.byKey(const Key('chat-processing-card')), findsOneWidget);
      expect(find.byKey(const Key('chat-message-field')), findsNothing);
      expect(api.eventCalls, 1);
      expect(api.chatCalls, 0);

      await tester.tap(
        find.descendant(
          of: find.byType(InterviewChatPage),
          matching: find.byIcon(Icons.arrow_back_ios_new),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 250));
      await tester.tap(find.text('确定退出'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 350));

      expect(find.text('首页标记'), findsOneWidget);
      expect(api.cancelCalls, 0);
    },
  );

  testWidgets(
    'opening failure shows recovery actions and no ordinary answer composer',
    (tester) async {
      final api = _DurableStartApi();
      addTearDown(api.dispose);
      final service = InterviewService(
        api,
        turnIdFactory: () => 'retry-1',
        pendingStartStore: _MemoryPendingStartStore(),
        reconnectDelays: const [],
      );
      service.restoreAttempt(_attempt('opening-failed', 'FAILED'));

      await _pumpChat(tester, service);

      expect(find.byKey(const Key('chat-recovery-card')), findsOneWidget);
      expect(find.byKey(const Key('chat-message-field')), findsNothing);
      expect(find.text('重试本轮'), findsOneWidget);
      expect(find.text('丢弃本轮'), findsOneWidget);

      await tester.tap(find.text('重试本轮'));
      await tester.pump();
      expect(api.retryCalls, 1);
      expect(api.lastRetryVersion, 1);
      expect(api.lastRetryTail, isNull);
      expect(api.chatCalls, 0);
    },
  );

  testWidgets(
    'live chat answers use durable turns without optimistic bubbles',
    (tester) async {
      final api = _DurableStartApi();
      addTearDown(api.dispose);
      final service = InterviewService(
        api,
        turnIdFactory: () => 'tail-1',
        pendingStartStore: _MemoryPendingStartStore(),
        reconnectDelays: const [],
      );
      service.hydrateTranscript(_activeTranscript());
      final before = service.messages.length;

      await _pumpChat(tester, service);
      await tester.enterText(
        find.byKey(const Key('chat-message-field')),
        '我的持久化回答',
      );
      await tester.tap(find.byKey(const Key('chat-send')));
      await tester.pump();

      expect(api.turnCalls, 1);
      expect(api.lastTurnVersion, 3);
      expect(api.lastTurnTail, 3);
      expect(api.chatCalls, 0);
      expect(service.messages, hasLength(before));
      expect(find.text('我的持久化回答'), findsNothing);
      expect(find.byKey(const Key('chat-processing-card')), findsOneWidget);
    },
  );

  testWidgets(
    'exhausted terminal refresh shows a retry that reloads canonical history',
    (tester) async {
      final api = _DurableStartApi()
        ..startStatus = 'COMPLETED'
        ..treeErrorsRemaining = 1;
      addTearDown(api.dispose);
      final service = InterviewService(
        api,
        turnIdFactory: () => 'completed-opening',
        pendingStartStore: _MemoryPendingStartStore(),
        reconnectDelays: const [],
      );

      await service.startNewInterview();
      expect(service.currentTranscript, isNull);
      expect(service.replayError, contains('transient tree failure'));
      await _pumpChat(tester, service);

      expect(find.byKey(const Key('chat-replay-error')), findsOneWidget);
      expect(find.byKey(const Key('chat-replay-retry')), findsOneWidget);
      await tester.tap(find.byKey(const Key('chat-replay-retry')));
      await tester.pumpAndSettle();

      expect(api.treeCalls, 2);
      expect(api.transcriptCalls, 1);
      expect(find.byKey(const Key('chat-replay-error')), findsNothing);
      expect(find.text('请介绍项目'), findsOneWidget);
    },
  );
}

Future<void> _pumpFlow(WidgetTester tester, InterviewService service) {
  return tester.pumpWidget(
    MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: service),
        ChangeNotifierProvider(
          create: (_) => ResumeService(ResumeApi(ApiClient())),
        ),
      ],
      child: MaterialApp(
        initialRoute: '/upload',
        routes: {
          '/upload': (_) => const UploadResumePage(),
          '/chat': (_) => const InterviewChatPage(),
          '/home': (_) => const Scaffold(body: Text('首页标记')),
        },
      ),
    ),
  );
}

Future<void> _pumpChat(WidgetTester tester, InterviewService service) {
  return tester.pumpWidget(
    ChangeNotifierProvider.value(
      value: service,
      child: const MaterialApp(home: InterviewChatPage()),
    ),
  );
}

BranchTranscript _activeTranscript() => const BranchTranscript(
  lineageId: 'lineage-1',
  branchId: 'root-1',
  branchLabel: '原始分支',
  stage: 'project_qna',
  status: 1,
  branchVersion: 3,
  messages: [
    BranchMessage(
      id: 3,
      owningBranchId: 'root-1',
      role: 'ai',
      messageType: 'ai_question',
      content: '请介绍项目',
      stage: 'project_qna',
      sequence: 1,
      expectsResponse: true,
      deliveryStatus: 'completed',
      inherited: false,
      forkable: true,
      createdAt: null,
    ),
  ],
);

TurnAttempt _attempt(String turnId, String status) => TurnAttempt(
  turnId: turnId,
  lineageId: 'lineage-1',
  branchId: 'root-1',
  expectedBranchVersion: 1,
  expectedTailMessageId: null,
  candidateAnswer: '我准备好了',
  status: status,
  errorCode: status == 'FAILED' ? 'MODEL_ERROR' : null,
);

class _MemoryPendingStartStore implements PendingStartStore {
  PendingInterviewStart? value;

  @override
  Future<PendingInterviewStart?> load() async => value;

  @override
  Future<void> save(PendingInterviewStart pending) async {
    value = pending;
  }

  @override
  Future<void> clear(PendingInterviewStart expected) async {
    if (value == expected) value = null;
  }
}

class _DurableStartApi extends InterviewApi {
  _DurableStartApi() : super(ApiClient());

  Completer<void>? startGate;
  int startCalls = 0;
  int eventCalls = 0;
  int turnCalls = 0;
  int retryCalls = 0;
  int cancelCalls = 0;
  int chatCalls = 0;
  int treeCalls = 0;
  int transcriptCalls = 0;
  int treeErrorsRemaining = 0;
  String startStatus = 'PROCESSING';
  int? lastTurnVersion;
  int? lastTurnTail;
  int? lastRetryVersion;
  int? lastRetryTail;
  final StreamController<TurnAttemptEvent> events =
      StreamController<TurnAttemptEvent>.broadcast();

  Future<void> dispose() => events.close();

  @override
  Future<StartAttempt> startAttempt({
    required String turnId,
    int? resumeId,
    int? jobId,
  }) async {
    startCalls++;
    await startGate?.future;
    return StartAttempt(
      lineageId: 'lineage-1',
      branchId: 'root-1',
      attempt: _attempt(turnId, startStatus),
    );
  }

  @override
  Future<LineageTree> getLineageTree(String lineageId) async {
    treeCalls++;
    if (treeErrorsRemaining > 0) {
      treeErrorsRemaining--;
      throw StateError('transient tree failure');
    }
    return const LineageTree(
      lineageId: 'lineage-1',
      rootBranchId: 'root-1',
      focusedBranchId: 'root-1',
      nodes: [],
    );
  }

  @override
  Future<BranchTranscript> getBranchTranscript(String branchId) async {
    transcriptCalls++;
    return _activeTranscript();
  }

  @override
  Stream<TurnAttemptEvent> getTurnAttemptEvents(String turnId) {
    eventCalls++;
    return events.stream;
  }

  @override
  Future<TurnAttempt> getTurnAttempt(String turnId) async {
    return _attempt(turnId, 'PROCESSING');
  }

  @override
  Future<TurnAttempt> createTurnAttempt({
    required String branchId,
    required String turnId,
    required String candidateAnswer,
    required int expectedBranchVersion,
    required int? expectedTailMessageId,
  }) async {
    turnCalls++;
    lastTurnVersion = expectedBranchVersion;
    lastTurnTail = expectedTailMessageId;
    return TurnAttempt(
      turnId: turnId,
      lineageId: 'lineage-1',
      branchId: branchId,
      expectedBranchVersion: expectedBranchVersion,
      expectedTailMessageId: expectedTailMessageId,
      candidateAnswer: candidateAnswer,
      status: 'PROCESSING',
    );
  }

  @override
  Future<TurnAttempt> retryTurnAttempt({
    required String originalTurnId,
    required String turnId,
    required String candidateAnswer,
    required int expectedBranchVersion,
    required int? expectedTailMessageId,
  }) async {
    retryCalls++;
    lastRetryVersion = expectedBranchVersion;
    lastRetryTail = expectedTailMessageId;
    return TurnAttempt(
      turnId: turnId,
      lineageId: 'lineage-1',
      branchId: 'root-1',
      expectedBranchVersion: expectedBranchVersion,
      expectedTailMessageId: expectedTailMessageId,
      candidateAnswer: candidateAnswer,
      retryOfTurnId: originalTurnId,
      status: 'PROCESSING',
    );
  }

  @override
  Future<TurnAttempt> cancelTurnAttempt(String turnId) async {
    cancelCalls++;
    return _attempt(turnId, 'CANCEL_REQUESTED');
  }

  @override
  Future<void> chat({
    required String? sessionId,
    required String message,
    required String? resumeId,
    required String? jobId,
    required Function(String event, Map<String, dynamic> data) onEvent,
    required Function(dynamic error) onError,
    required Function() onDone,
  }) async {
    chatCalls++;
  }
}
