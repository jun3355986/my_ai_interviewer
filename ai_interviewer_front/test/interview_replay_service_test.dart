import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/api/interview_api.dart';
import 'package:ai_interviewer_front/models/interview_history.dart';
import 'package:ai_interviewer_front/services/interview_service.dart';
import 'package:ai_interviewer_front/services/pending_start_store.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues(const {});
  });

  test(
    'opening replay loads tree and transcript without a legacy chat call',
    () async {
      final api = _ReplayApi();
      final service = InterviewService(api, turnIdFactory: () => 'start-1');

      await service.loadReplay('lineage-1', branchId: 'root-1');

      expect(service.tree?.lineageId, 'lineage-1');
      expect(service.currentTranscript?.branchId, 'root-1');
      expect(api.treeCalls, 1);
      expect(api.transcriptCalls, 1);
      expect(api.chatCalls, 0);
    },
  );

  test(
    'loading a processing branch automatically reattaches its durable event stream',
    () async {
      final events = StreamController<TurnAttemptEvent>();
      final api = _ReplayApi()
        ..processingInTree = true
        ..eventStream = events.stream
        ..durableAttempt = _attempt('tree-processing', 'PROCESSING');
      final service = InterviewService(api);

      await service.loadReplay('lineage-1', branchId: 'root-1');
      await Future<void>.delayed(Duration.zero);

      expect(service.activeAttempt?.turnId, 'tree-processing');
      expect(api.eventCalls, 1);
      await events.close();
      await Future<void>.delayed(Duration.zero);
    },
  );

  test(
    'durable start and tail submission never add optimistic messages',
    () async {
      final api = _ReplayApi();
      final ids = ['start-1', 'turn-1'].iterator;
      final service = InterviewService(
        api,
        turnIdFactory: () {
          ids.moveNext();
          return ids.current;
        },
      );

      final start = await service.startNewInterview(resumeId: 20, jobId: 10);
      await Future<void>.delayed(Duration.zero);
      expect(start.attempt.turnId, 'start-1');
      expect(api.startCalls, 1);
      expect(api.eventCalls, 1);
      expect(api.chatCalls, 0);

      service.restoreAttempt(_attempt('start-1', 'COMPLETED'));
      await service.loadReplay('lineage-1', branchId: 'root-1');
      final before = service.messages.length;
      await service.submitTail('我的回答');

      expect(api.lastTurnBranchId, 'root-1');
      expect(api.lastTurnVersion, 3);
      expect(api.lastTurnTail, 3);
      expect(api.lastTurnAnswer, '我的回答');
      expect(service.messages.length, before);
      expect(service.isProcessing, isTrue);
      await service.submitTail('第二次提交');
      expect(api.turnCalls, 1);
    },
  );

  test(
    'opening completion reloads the root transcript and enables the first answer',
    () async {
      final api = _ReplayApi()
        ..eventStream = Stream<TurnAttemptEvent>.fromIterable([
          _event('opening-turn', 'COMPLETED'),
        ]);
      final service = InterviewService(
        api,
        turnIdFactory: () => 'opening-turn',
      );

      await service.startNewInterview();
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);

      expect(api.treeCalls, 1);
      expect(api.transcriptCalls, 1);
      expect(service.currentTranscript?.branchId, 'root-1');
      expect(service.messages, hasLength(3));
      expect(service.canReplyAtTail, isTrue);
      expect(service.activeAttempt, isNull);
    },
  );

  test(
    'completed durable start replay immediately reloads canonical root history',
    () async {
      final api = _ReplayApi()..startStatus = 'COMPLETED';
      final service = InterviewService(
        api,
        turnIdFactory: () => 'completed-start-replay',
        reconnectDelays: const [],
      );

      final started = await service.startNewInterview(resumeId: 20, jobId: 10);

      expect(started.attempt.status, 'COMPLETED');
      expect(api.eventCalls, 0);
      expect(api.treeCalls, 1);
      expect(api.transcriptCalls, 1);
      expect(service.currentTranscript?.branchId, 'root-1');
      expect(service.messages, hasLength(3));
      expect(service.activeAttempt, isNull);
    },
  );

  test(
    'completed replay retries a transient canonical refresh before fencing the turn',
    () async {
      final api = _ReplayApi()
        ..startStatus = 'COMPLETED'
        ..treeErrorsRemaining = 1;
      final observedDelays = <Duration>[];
      final service = InterviewService(
        api,
        turnIdFactory: () => 'completed-refresh-retry',
        reconnectDelays: const [Duration(milliseconds: 25)],
        reconnectDelay: (delay) async => observedDelays.add(delay),
      );

      await service.startNewInterview(resumeId: 20, jobId: 10);

      expect(api.treeCalls, 2);
      expect(api.transcriptCalls, 1);
      expect(observedDelays, const [Duration(milliseconds: 25)]);
      expect(service.currentTranscript?.branchId, 'root-1');
      expect(service.replayError, isNull);
    },
  );

  test(
    'completed exact tail replay refreshes canonical branch state before returning',
    () async {
      final api = _ReplayApi()..turnStatus = 'COMPLETED';
      final service = InterviewService(
        api,
        turnIdFactory: () => 'completed-tail-replay',
        reconnectDelays: const [],
      );
      await service.loadReplay('lineage-1', branchId: 'root-1');
      api.treeCalls = 0;
      api.transcriptCalls = 0;

      await service.submitTail('已由服务端提交的回答');

      expect(api.turnCalls, 1);
      expect(api.eventCalls, 0);
      expect(api.treeCalls, 1);
      expect(api.transcriptCalls, 1);
      expect(service.currentTranscript?.branchId, 'root-1');
      expect(service.activeAttempt, isNull);
    },
  );

  test(
    'durable start reuses one turn id after lost response and deduplicates double tap',
    () async {
      final api = _ReplayApi()..startErrorOnce = true;
      var factoryCalls = 0;
      final service = InterviewService(
        api,
        turnIdFactory: () {
          factoryCalls++;
          return 'stable-start-$factoryCalls';
        },
      );

      await expectLater(
        service.startNewInterview(resumeId: 20, jobId: 10),
        throwsStateError,
      );
      final retried = await service.startNewInterview(resumeId: 20, jobId: 10);

      expect(retried.attempt.turnId, 'stable-start-1');
      expect(api.startTurnIds, ['stable-start-1', 'stable-start-1']);
      expect(factoryCalls, 1);

      final gate = Completer<void>();
      api.startGate = gate;
      final first = service.startNewInterview(resumeId: 21, jobId: 10);
      final second = service.startNewInterview(resumeId: 21, jobId: 10);
      await Future<void>.delayed(Duration.zero);
      expect(api.startCalls, 3);
      gate.complete();
      final results = await Future.wait([first, second]);
      expect(results[0].attempt.turnId, results[1].attempt.turnId);
      expect(factoryCalls, 2);
    },
  );

  test(
    'durable start reuses persisted turn id after service recreation and clears it on success',
    () async {
      final api = _ReplayApi()..startErrorOnce = true;
      var factoryCalls = 0;
      String nextTurnId() => 'restart-safe-${++factoryCalls}';
      final firstService = InterviewService(
        api,
        turnIdFactory: nextTurnId,
        reconnectDelays: const [],
      );

      await expectLater(
        firstService.startNewInterview(resumeId: 20, jobId: 10),
        throwsStateError,
      );

      final recreatedService = InterviewService(
        api,
        turnIdFactory: nextTurnId,
        reconnectDelays: const [],
      );
      final recovered = await recreatedService.startNewInterview(
        resumeId: 20,
        jobId: 10,
      );

      expect(recovered.attempt.turnId, 'restart-safe-1');
      expect(api.startTurnIds, ['restart-safe-1', 'restart-safe-1']);
      expect(api.committedStartTurnIds, {'restart-safe-1'});
      expect(factoryCalls, 1);

      final nextService = InterviewService(
        api,
        turnIdFactory: nextTurnId,
        reconnectDelays: const [],
      );
      final fresh = await nextService.startNewInterview(
        resumeId: 20,
        jobId: 10,
      );

      expect(fresh.attempt.turnId, 'restart-safe-2');
      expect(api.committedStartTurnIds, {'restart-safe-1', 'restart-safe-2'});
    },
  );

  test(
    'changed start payload replaces an injected pending key before request',
    () async {
      final store = _MemoryPendingStartStore(
        const PendingInterviewStart(
          turnId: 'stale-start',
          resumeId: 20,
          jobId: 10,
        ),
      );
      final api = _ReplayApi();
      final service = InterviewService(
        api,
        turnIdFactory: () => 'replacement-start',
        pendingStartStore: store,
        reconnectDelays: const [],
      );

      final started = await service.startNewInterview(resumeId: 21, jobId: 10);

      expect(started.attempt.turnId, 'replacement-start');
      expect(api.startTurnIds, ['replacement-start']);
      expect(store.saved, [
        const PendingInterviewStart(
          turnId: 'replacement-start',
          resumeId: 21,
          jobId: 10,
        ),
      ]);
      expect(store.value, isNull);
      expect(store.clearCalls, 1);
    },
  );

  test(
    'late success clears only its own pending start and preserves a newer replacement',
    () async {
      final store = _MemoryPendingStartStore();
      final firstGate = Completer<void>();
      final firstApi = _ReplayApi()..startGate = firstGate;
      final firstService = InterviewService(
        firstApi,
        turnIdFactory: () => 'first-pending',
        pendingStartStore: store,
        reconnectDelays: const [],
      );
      final firstStart = firstService.startNewInterview(
        resumeId: 20,
        jobId: 10,
      );
      await Future<void>.delayed(Duration.zero);

      final secondApi = _ReplayApi()..startErrorOnce = true;
      final secondService = InterviewService(
        secondApi,
        turnIdFactory: () => 'newer-pending',
        pendingStartStore: store,
        reconnectDelays: const [],
      );
      await expectLater(
        secondService.startNewInterview(resumeId: 21, jobId: 10),
        throwsStateError,
      );
      expect(store.value?.turnId, 'newer-pending');

      firstGate.complete();
      await firstStart;

      expect(
        store.value,
        const PendingInterviewStart(
          turnId: 'newer-pending',
          resumeId: 21,
          jobId: 10,
        ),
      );
    },
  );

  test(
    'candidate and AI fork drafts are local until explicit submit',
    () async {
      final api = _ReplayApi();
      final service = InterviewService(api, turnIdFactory: () => 'fork-1');
      await service.loadReplay('lineage-1', branchId: 'root-1');
      final candidate = service.currentTranscript!.messages[1];
      final prompt = service.currentTranscript!.messages.first;

      service.prepareFork(candidate);
      expect(service.branchDraft?.answer, '原回答');
      expect(api.forkCalls, 0);
      service.prepareFork(prompt);
      expect(service.branchDraft?.answer, '');
      expect(api.forkCalls, 0);

      service.prepareFork(candidate);
      service.updateForkDraft('编辑后的回答');
      await service.submitFork();

      expect(api.lastForkFocusedBranchId, 'root-1');
      expect(api.lastForkTriggerMessageId, candidate.id);
      expect(api.lastForkVersion, 3);
      expect(api.lastForkTail, 3);
      expect(api.lastForkAnswer, '编辑后的回答');
      expect(service.currentTranscript?.branchId, 'child-1');
      expect(service.activeAttempt?.turnId, 'fork-1');
    },
  );

  test(
    'reattachment is single-flight and reloads canonical state once on completion',
    () async {
      final api = _ReplayApi();
      final events = StreamController<TurnAttemptEvent>();
      api.eventStream = events.stream;
      final service = InterviewService(api, turnIdFactory: () => 'unused');
      await service.loadReplay('lineage-1', branchId: 'root-1');
      service.restoreAttempt(_attempt('turn-processing', 'PROCESSING'));

      final first = service.attachToActiveAttempt();
      final second = service.attachToActiveAttempt();
      events.add(_event('turn-processing', 'PROCESSING'));
      events.add(_event('turn-processing', 'COMPLETED'));
      await events.close();
      await Future.wait([first, second]);

      expect(api.eventCalls, 1);
      expect(api.treeCalls, 2);
      expect(api.transcriptCalls, 2);
      expect(service.activeAttempt, isNull);
      expect(service.recoveryAttempt, isNull);
    },
  );

  test(
    'event disconnect automatically reconnects to terminal after one business attach',
    () async {
      final api = _ReplayApi();
      api.eventStreams.add(
        Stream<TurnAttemptEvent>.error(StateError('disconnect')),
      );
      api.eventStreams.add(
        Stream.fromIterable([
          _event('turn-reconnect', 'PROCESSING'),
          _event('turn-reconnect', 'COMPLETED'),
        ]),
      );
      api.durableAttempt = _attempt('turn-reconnect', 'PROCESSING');
      final service = InterviewService(api);
      await service.loadReplay('lineage-1', branchId: 'root-1');
      service.restoreAttempt(_attempt('turn-reconnect', 'PROCESSING'));

      await service.attachToActiveAttempt();
      expect(api.getAttemptCalls, 1);
      expect(api.eventCalls, 2);
      expect(api.treeCalls, 2);
      expect(api.transcriptCalls, 2);
      expect(service.activeAttempt, isNull);
    },
  );

  test('event reconnect uses bounded backoff without a tight loop', () async {
    final api = _ReplayApi()
      ..durableAttempt = _attempt('turn-bounded', 'PROCESSING')
      ..eventStreams.addAll([
        Stream<TurnAttemptEvent>.error(StateError('disconnect-1')),
        Stream<TurnAttemptEvent>.error(StateError('disconnect-2')),
        Stream<TurnAttemptEvent>.error(StateError('disconnect-3')),
      ]);
    final observedDelays = <Duration>[];
    final service = InterviewService(
      api,
      reconnectDelays: const [
        Duration(milliseconds: 10),
        Duration(milliseconds: 20),
      ],
      reconnectDelay: (delay) async => observedDelays.add(delay),
    );
    await service.loadReplay('lineage-1', branchId: 'root-1');
    service.restoreAttempt(_attempt('turn-bounded', 'PROCESSING'));

    await service.attachToActiveAttempt();

    expect(api.eventCalls, 3);
    expect(api.getAttemptCalls, 3);
    expect(observedDelays, const [
      Duration(milliseconds: 10),
      Duration(milliseconds: 20),
    ]);
    expect(service.activeAttempt?.turnId, 'turn-bounded');
  });

  test(
    'branch switch cancels a delayed reconnect for the old attempt',
    () async {
      final api = _ReplayApi()
        ..durableAttempt = _attempt('turn-old-branch', 'PROCESSING')
        ..eventStreams.add(
          Stream<TurnAttemptEvent>.error(StateError('disconnect')),
        );
      final delayStarted = Completer<void>();
      final releaseDelay = Completer<void>();
      final service = InterviewService(
        api,
        reconnectDelays: const [Duration(seconds: 1)],
        reconnectDelay: (_) {
          delayStarted.complete();
          return releaseDelay.future;
        },
      );
      await service.loadReplay('lineage-1', branchId: 'root-1');
      service.restoreAttempt(_attempt('turn-old-branch', 'PROCESSING'));

      final attachment = service.attachToActiveAttempt();
      await delayStarted.future;
      await service.selectBranch('child-1');
      releaseDelay.complete();
      await attachment;

      expect(api.eventCalls, 1);
      expect(service.currentTranscript?.branchId, 'child-1');
      expect(service.activeAttempt, isNull);
    },
  );

  test(
    'replacement attempt prevents stale reconnect on the same branch',
    () async {
      final api = _ReplayApi()
        ..durableAttempt = _attempt('turn-stale', 'PROCESSING')
        ..eventStreams.add(
          Stream<TurnAttemptEvent>.error(StateError('disconnect')),
        );
      final delayStarted = Completer<void>();
      final releaseDelay = Completer<void>();
      final service = InterviewService(
        api,
        reconnectDelays: const [Duration(seconds: 1)],
        reconnectDelay: (_) {
          delayStarted.complete();
          return releaseDelay.future;
        },
      );
      await service.loadReplay('lineage-1', branchId: 'root-1');
      service.restoreAttempt(_attempt('turn-stale', 'PROCESSING'));

      final attachment = service.attachToActiveAttempt();
      await delayStarted.future;
      service.restoreAttempt(_attempt('turn-replacement', 'PROCESSING'));
      releaseDelay.complete();
      await attachment;

      expect(api.eventCalls, 1);
      expect(service.activeAttempt?.turnId, 'turn-replacement');
    },
  );

  test(
    'selecting a branch without recoverable work clears old attempt and branch-bound tail draft',
    () async {
      final api = _ReplayApi()..throwConflict = true;
      final service = InterviewService(api, turnIdFactory: () => 'child-turn');
      await service.loadReplay('lineage-1', branchId: 'root-1');
      service.restoreAttempt(_attempt('old-failure', 'FAILED'));
      await service.submitTail('root draft');
      expect(service.tailDraft, 'root draft');

      api.throwConflict = false;
      await service.selectBranch('child-1');

      expect(service.activeAttempt, isNull);
      expect(service.recoveryAttempt, isNull);
      expect(service.tailDraft, isEmpty);
      await service.submitTail('child answer');
      expect(api.lastTurnBranchId, 'child-1');
      expect(api.lastTurnVersion, 1);
    },
  );

  test(
    'late events from an old branch cannot replace or refresh the current selection',
    () async {
      final api = _ReplayApi();
      final oldEvents = StreamController<TurnAttemptEvent>();
      api.eventStream = oldEvents.stream;
      final service = InterviewService(api);
      await service.loadReplay('lineage-1', branchId: 'root-1');
      service.restoreAttempt(_attempt('old-turn', 'PROCESSING'));
      final oldAttachment = service.attachToActiveAttempt();

      await service.selectBranch('child-1');
      oldEvents.add(_event('old-turn', 'COMPLETED'));
      await oldEvents.close();
      await oldAttachment;

      expect(service.currentTranscript?.branchId, 'child-1');
      expect(service.activeAttempt, isNull);
      expect(service.recoveryAttempt, isNull);
      expect(api.treeCalls, 1);
      expect(api.transcriptCalls, 2);
    },
  );

  test(
    'failed attempt stays outside transcript and supports retry cancel discard',
    () async {
      final api = _ReplayApi();
      final ids = ['retry-1'].iterator;
      final service = InterviewService(
        api,
        turnIdFactory: () {
          ids.moveNext();
          return ids.current;
        },
      );
      await service.loadReplay('lineage-1', branchId: 'root-1');
      final before = service.messages.length;
      service.restoreAttempt(_attempt('failed-1', 'FAILED'));

      expect(service.recoveryAttempt?.candidateAnswer, '原回答');
      expect(service.messages.length, before);
      await service.retryRecovery('修改后重试');
      expect(api.retryCalls, 1);
      expect(api.lastRetryOf, 'failed-1');
      expect(api.lastRetryAnswer, '修改后重试');

      service.restoreAttempt(_attempt('processing-2', 'PROCESSING'));
      await service.cancelActiveAttempt();
      expect(api.cancelCalls, 1);
      service.restoreAttempt(_attempt('cancelled-1', 'CANCELLED'));
      await service.discardRecovery();
      expect(api.discardCalls, 1);
      expect(service.recoveryAttempt, isNull);
    },
  );

  test(
    'conflict preserves tail and fork drafts and offers explicit refresh',
    () async {
      final api = _ReplayApi()..throwConflict = true;
      final ids = ['turn-conflict', 'fork-conflict'].iterator;
      final service = InterviewService(
        api,
        turnIdFactory: () {
          ids.moveNext();
          return ids.current;
        },
      );
      await service.loadReplay('lineage-1', branchId: 'root-1');

      await service.submitTail('保留的普通回答');
      expect(service.tailDraft, '保留的普通回答');
      expect(service.conflictMessage, contains('刷新'));

      service.prepareFork(service.currentTranscript!.messages[1]);
      service.updateForkDraft('保留的分支回答');
      await service.submitFork();
      expect(service.branchDraft?.answer, '保留的分支回答');
      expect(service.conflictMessage, contains('刷新'));

      api.throwConflict = false;
      await service.refreshReplay();
      expect(service.tailDraft, '保留的普通回答');
      expect(service.branchDraft?.answer, '保留的分支回答');
    },
  );

  test(
    'branch switch preserves but cannot submit or overwrite another branch draft',
    () async {
      final api = _ReplayApi();
      final service = InterviewService(api);
      await service.loadReplay('lineage-1', branchId: 'root-1');
      service.prepareFork(service.currentTranscript!.messages[1]);
      service.updateForkDraft('root branch draft');

      await service.selectBranch('child-1');

      expect(service.branchDraft?.focusedBranchId, 'root-1');
      expect(service.branchDraft?.answer, 'root branch draft');
      expect(service.hasForkDraftForCurrentBranch, isFalse);
      service.prepareFork(service.currentTranscript!.messages[1]);
      expect(service.branchDraft?.focusedBranchId, 'root-1');
      expect(service.conflictMessage, contains('另一分支'));

      service.clearForkDraft();
      service.prepareFork(service.currentTranscript!.messages[1]);
      expect(service.branchDraft?.focusedBranchId, 'child-1');
      expect(service.hasForkDraftForCurrentBranch, isTrue);
    },
  );
}

TurnAttempt _attempt(String id, String status) => TurnAttempt(
  turnId: id,
  lineageId: 'lineage-1',
  branchId: 'root-1',
  expectedBranchVersion: 3,
  expectedTailMessageId: 2,
  candidateAnswer: '原回答',
  status: status,
);

TurnAttemptEvent _event(String id, String status) => TurnAttemptEvent(
  turnId: id,
  sequence: 1,
  type: status.toLowerCase(),
  status: status,
);

class _MemoryPendingStartStore implements PendingStartStore {
  _MemoryPendingStartStore([this.value]);

  PendingInterviewStart? value;
  final List<PendingInterviewStart> saved = [];
  int clearCalls = 0;

  @override
  Future<PendingInterviewStart?> load() async => value;

  @override
  Future<void> save(PendingInterviewStart pending) async {
    value = pending;
    saved.add(pending);
  }

  @override
  Future<void> clear(PendingInterviewStart expected) async {
    if (value == expected) value = null;
    clearCalls++;
  }
}

class _ReplayApi extends InterviewApi {
  _ReplayApi() : super(ApiClient());

  int treeCalls = 0;
  int transcriptCalls = 0;
  int startCalls = 0;
  int turnCalls = 0;
  int forkCalls = 0;
  int eventCalls = 0;
  int retryCalls = 0;
  int cancelCalls = 0;
  int discardCalls = 0;
  int chatCalls = 0;
  bool throwConflict = false;
  bool startErrorOnce = false;
  bool processingInTree = false;
  String startStatus = 'PROCESSING';
  String turnStatus = 'PROCESSING';
  int treeErrorsRemaining = 0;
  Completer<void>? startGate;
  final List<String> startTurnIds = [];
  final Set<String> committedStartTurnIds = {};
  Stream<TurnAttemptEvent>? eventStream;
  final List<Stream<TurnAttemptEvent>> eventStreams = [];
  TurnAttempt? durableAttempt;
  int getAttemptCalls = 0;
  String? lastTurnBranchId;
  int? lastTurnVersion;
  int? lastTurnTail;
  String? lastTurnAnswer;
  String? lastForkFocusedBranchId;
  int? lastForkTriggerMessageId;
  int? lastForkVersion;
  int? lastForkTail;
  String? lastForkAnswer;
  String? lastRetryOf;
  String? lastRetryAnswer;

  @override
  Future<LineageTree> getLineageTree(String lineageId) async {
    treeCalls++;
    if (treeErrorsRemaining > 0) {
      treeErrorsRemaining--;
      throw StateError('transient tree failure');
    }
    return LineageTree(
      lineageId: lineageId,
      rootBranchId: 'root-1',
      focusedBranchId: 'root-1',
      nodes: [
        LineageTreeNode(
          branchId: 'root-1',
          branchLabel: '原始分支',
          stage: 'project_qna',
          status: 1,
          branchVersion: 3,
          progress: 40,
          ownedAssessmentCount: 1,
          inheritedAssessmentCount: 0,
          totalAssessmentCount: 1,
          recoverableTurnId: processingInTree ? 'tree-processing' : null,
          recoverableTurnStatus: processingInTree ? 'PROCESSING' : null,
        ),
        const LineageTreeNode(
          branchId: 'child-1',
          parentBranchId: 'root-1',
          branchLabel: '分支 1',
          stage: 'project_qna',
          status: 1,
          branchVersion: 1,
          progress: 42,
          ownedAssessmentCount: 0,
          inheritedAssessmentCount: 1,
          totalAssessmentCount: 1,
        ),
      ],
    );
  }

  @override
  Future<BranchTranscript> getBranchTranscript(String branchId) async {
    transcriptCalls++;
    return BranchTranscript(
      lineageId: 'lineage-1',
      branchId: branchId,
      branchLabel: branchId == 'child-1' ? '分支 1' : '原始分支',
      parentBranchId: branchId == 'child-1' ? 'root-1' : null,
      forkPointMessageId: branchId == 'child-1' ? 1 : null,
      stage: 'project_qna',
      status: 1,
      branchVersion: branchId == 'child-1' ? 1 : 3,
      messages: [
        const BranchMessage(
          id: 1,
          owningBranchId: 'root-1',
          role: 'ai',
          messageType: 'ai_question',
          content: '问题',
          stage: 'project_qna',
          sequence: 1,
          expectsResponse: true,
          deliveryStatus: 'completed',
          inherited: false,
          forkable: true,
          forkPointMessageId: 1,
          createdAt: null,
        ),
        const BranchMessage(
          id: 2,
          owningBranchId: 'root-1',
          role: 'human',
          messageType: 'candidate_answer',
          content: '原回答',
          stage: 'project_qna',
          sequence: 2,
          expectsResponse: false,
          deliveryStatus: 'completed',
          inherited: false,
          forkable: true,
          forkPointMessageId: 1,
          createdAt: null,
        ),
        const BranchMessage(
          id: 3,
          owningBranchId: 'root-1',
          role: 'ai',
          messageType: 'ai_question',
          content: '等待回答的问题',
          stage: 'project_qna',
          sequence: 3,
          expectsResponse: true,
          deliveryStatus: 'completed',
          inherited: false,
          forkable: true,
          forkPointMessageId: 3,
          createdAt: null,
        ),
      ],
    );
  }

  @override
  Future<StartAttempt> startAttempt({
    required String turnId,
    int? resumeId,
    int? jobId,
  }) async {
    startCalls++;
    startTurnIds.add(turnId);
    committedStartTurnIds.add(turnId);
    if (startErrorOnce) {
      startErrorOnce = false;
      throw StateError('response lost');
    }
    await startGate?.future;
    return StartAttempt(
      lineageId: 'lineage-1',
      branchId: 'root-1',
      attempt: _attempt(turnId, startStatus),
    );
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
    lastTurnBranchId = branchId;
    lastTurnVersion = expectedBranchVersion;
    lastTurnTail = expectedTailMessageId;
    lastTurnAnswer = candidateAnswer;
    if (throwConflict) throw StateError('BRANCH_VERSION_CONFLICT');
    return _attempt(turnId, turnStatus);
  }

  @override
  Future<ForkAttempt> createForkAttempt({
    required String focusedBranchId,
    required String turnId,
    required int triggerMessageId,
    required String candidateAnswer,
    required int expectedFocusedBranchVersion,
    required int? expectedFocusedTailMessageId,
  }) async {
    forkCalls++;
    lastForkFocusedBranchId = focusedBranchId;
    lastForkTriggerMessageId = triggerMessageId;
    lastForkVersion = expectedFocusedBranchVersion;
    lastForkTail = expectedFocusedTailMessageId;
    lastForkAnswer = candidateAnswer;
    if (throwConflict) throw StateError('BRANCH_TAIL_CONFLICT');
    return ForkAttempt(
      branchId: 'child-1',
      attempt: TurnAttempt(
        turnId: turnId,
        lineageId: 'lineage-1',
        branchId: 'child-1',
        expectedBranchVersion: 1,
        expectedTailMessageId: 1,
        candidateAnswer: candidateAnswer,
        status: 'PROCESSING',
      ),
    );
  }

  @override
  Stream<TurnAttemptEvent> getTurnAttemptEvents(String turnId) {
    eventCalls++;
    if (eventStreams.isNotEmpty) return eventStreams.removeAt(0);
    return eventStream ?? const Stream.empty();
  }

  @override
  Future<TurnAttempt> getTurnAttempt(String turnId) async {
    getAttemptCalls++;
    return durableAttempt ?? _attempt(turnId, 'PROCESSING');
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
    lastRetryOf = originalTurnId;
    lastRetryAnswer = candidateAnswer;
    return _attempt(turnId, 'PROCESSING');
  }

  @override
  Future<TurnAttempt> cancelTurnAttempt(String turnId) async {
    cancelCalls++;
    return _attempt(turnId, 'CANCEL_REQUESTED');
  }

  @override
  Future<TurnAttempt> discardTurnAttempt(String turnId) async {
    discardCalls++;
    return _attempt(turnId, 'DISCARDED');
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
