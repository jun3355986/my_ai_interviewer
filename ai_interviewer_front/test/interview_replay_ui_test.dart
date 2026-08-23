import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/api/interview_api.dart';
import 'package:ai_interviewer_front/history_detail_page.dart';
import 'package:ai_interviewer_front/models/interview_history.dart';
import 'package:ai_interviewer_front/models/question_media.dart';
import 'package:ai_interviewer_front/services/interview_service.dart';

void main() {
  testWidgets(
    'desktop shows split tree and transcript and node selection loads branch',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = _ReplayUiApi();
      final service = InterviewService(api, reconnectDelays: const []);

      await _pumpReplay(tester, service, mediaSize: const Size(1200, 600));

      expect(find.byKey(const Key('branch-tree-panel')), findsOneWidget);
      expect(find.byKey(const Key('replay-transcript')), findsOneWidget);
      expect(find.text('原始分支'), findsWidgets);
      expect(find.text('根分支问题'), findsOneWidget);
      expect(find.text('项目架构图'), findsOneWidget);
      expect(find.text('最新活动 07-24 09:30'), findsOneWidget);
      expect(find.text('评估：架构表达清晰'), findsOneWidget);

      final rootDepth = tester.widget<Padding>(
        find.byKey(const Key('branch-depth-root-1')),
      );
      final firstDepth = tester.widget<Padding>(
        find.byKey(const Key('branch-depth-child-1')),
      );
      final secondDepth = tester.widget<Padding>(
        find.byKey(const Key('branch-depth-child-2')),
      );
      final thirdDepth = tester.widget<Padding>(
        find.byKey(const Key('branch-depth-child-3')),
      );
      expect((rootDepth.padding as EdgeInsets).left, 0);
      expect((firstDepth.padding as EdgeInsets).left, 18);
      expect((secondDepth.padding as EdgeInsets).left, 36);
      expect((thirdDepth.padding as EdgeInsets).left, 54);

      await tester.tap(find.text('分支 1').first);
      await tester.pumpAndSettle();

      expect(service.currentTranscript?.branchId, 'child-1');
      expect(find.text('继承前缀'), findsWidgets);
      expect(find.text('Fork Point'), findsOneWidget);
      expect(find.text('当前分支增量'), findsOneWidget);
      expect(find.text('子分支回答'), findsOneWidget);
    },
  );

  testWidgets('narrow replay is transcript-first with a branch tree sheet', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(480, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final service = InterviewService(_ReplayUiApi(), reconnectDelays: const []);

    await _pumpReplay(tester, service, mediaSize: const Size(480, 600));

    expect(find.byKey(const Key('branch-tree-panel')), findsNothing);
    expect(find.byKey(const Key('replay-transcript')), findsOneWidget);
    expect(find.byKey(const Key('open-branch-tree')), findsOneWidget);

    await tester.tap(find.byKey(const Key('open-branch-tree')));
    await tester.pumpAndSettle();
    expect(find.text('选择面试分支'), findsOneWidget);
    expect(find.text('分支 1'), findsOneWidget);
  });

  testWidgets('completed branch is read-only but fork draft is explicit', (
    tester,
  ) async {
    final api = _ReplayUiApi()..completed = true;
    final service = InterviewService(
      api,
      turnIdFactory: () => 'fork-ui',
      reconnectDelays: const [],
    );
    await _pumpReplay(tester, service);

    expect(find.byKey(const Key('tail-composer')), findsNothing);
    expect(find.text('从此处分支'), findsWidgets);
    await tester.ensureVisible(find.text('从此处分支').last);
    await tester.tap(find.text('从此处分支').last);
    await tester.pump();

    final draftField = find.byKey(const Key('fork-draft-field'));
    expect(draftField, findsOneWidget);
    expect(tester.widget<TextField>(draftField).controller?.text, '完成分支回答');
    expect(api.forkCalls, 0);

    await tester.enterText(draftField, '编辑后的分支回答');
    await _scrollTranscriptTo(tester, find.byKey(const Key('submit-fork')));
    tester
        .widget<InkWell>(find.byKey(const Key('submit-fork')))
        .onTap!();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(api.forkCalls, 1);
    expect(find.byKey(const Key('processing-card')), findsOneWidget);
  });

  testWidgets('active tail submits durable attempt without optimistic bubble', (
    tester,
  ) async {
    final api = _ReplayUiApi();
    final service = InterviewService(
      api,
      turnIdFactory: () => 'turn-ui',
      reconnectDelays: const [],
    );
    await _pumpReplay(tester, service);
    final before = service.messages.length;

    expect(service.canReplyAtTail, isTrue);
    await _scrollTranscriptTo(
      tester,
      find.byKey(const Key('tail-composer-field')),
    );
    final composer = find.byKey(const Key('tail-composer-field'));
    expect(composer, findsOneWidget);
    await tester.enterText(composer, '不会乐观插入的回答');
    await tester.tap(find.byKey(const Key('submit-tail')));
    await tester.pump();

    expect(api.turnCalls, 1);
    expect(service.messages.length, before);
    expect(find.text('不会乐观插入的回答'), findsNothing);
    expect(find.byKey(const Key('processing-card')), findsOneWidget);
  });

  testWidgets(
    'processing and recovery cards stay outside canonical transcript',
    (tester) async {
      final service = InterviewService(
        _ReplayUiApi(),
        reconnectDelays: const [],
      );
      await _pumpReplay(tester, service);
      final before = service.messages.length;

      service.restoreAttempt(_attempt('processing-ui', 'PROCESSING'));
      await tester.pump();
      await _scrollTranscriptTo(
        tester,
        find.byKey(const Key('processing-card')),
      );
      expect(find.byKey(const Key('processing-card')), findsOneWidget);

      service.restoreAttempt(_attempt('failed-ui', 'FAILED'));
      await tester.pump();
      await _scrollTranscriptTo(
        tester,
        find.byKey(const Key('turn-recovery-card')),
      );
      expect(find.byKey(const Key('turn-recovery-card')), findsOneWidget);
      expect(find.text('重试本轮'), findsOneWidget);
      expect(find.text('丢弃本轮'), findsOneWidget);
      expect(service.messages.length, before);
      expect(find.textContaining('[System]'), findsNothing);
    },
  );

  testWidgets(
    'switching branches preserves an off-branch draft without exposing submit',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final service = InterviewService(
        _ReplayUiApi(),
        reconnectDelays: const [],
      );
      await _pumpReplay(tester, service, mediaSize: const Size(1200, 600));

      await tester.tap(find.text('从此处分支').first);
      await tester.pump();
      service.updateForkDraft('root draft');
      await tester.tap(find.text('分支 1').first);
      await tester.pumpAndSettle();

      expect(service.branchDraft?.focusedBranchId, 'root-1');
      expect(find.byKey(const Key('fork-draft-other-branch')), findsOneWidget);
      expect(find.byKey(const Key('fork-draft-field')), findsNothing);
      expect(find.byKey(const Key('submit-fork')), findsNothing);
    },
  );
}

Future<void> _pumpReplay(
  WidgetTester tester,
  InterviewService service, {
  Size mediaSize = const Size(1200, 600),
}) async {
  await tester.pumpWidget(
    ChangeNotifierProvider.value(
      value: service,
      child: MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(size: mediaSize),
          child: child!,
        ),
        onGenerateRoute: (_) => MaterialPageRoute<void>(
          settings: RouteSettings(arguments: _summary()),
          builder: (_) => const HistoryDetailPage(),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _scrollTranscriptTo(WidgetTester tester, Finder target) {
  return tester.scrollUntilVisible(
    target,
    260,
    scrollable: find
        .descendant(
          of: find.byKey(const Key('replay-transcript')),
          matching: find.byType(Scrollable),
        )
        .first,
  );
}

InterviewLineageSummary _summary() => const InterviewLineageSummary(
  lineageId: 'lineage-1',
  rootSessionId: 'root-1',
  candidateName: '测试候选人',
  resumeId: 20,
  jobId: 10,
  jobTitle: 'Java 后端工程师',
  branchCount: 2,
  activeBranchCount: 1,
  completedBranchCount: 1,
  bestCompletedScore: 88,
  latestActivityAt: null,
  focusedBranchId: 'root-1',
  focusedBranchStage: 'project_qna',
  focusedBranchStageDisplay: '项目提问',
  focusedBranchStatus: 1,
  focusedBranchProgress: 40,
);

TurnAttempt _attempt(String id, String status) => TurnAttempt(
  turnId: id,
  lineageId: 'lineage-1',
  branchId: 'root-1',
  expectedBranchVersion: 3,
  expectedTailMessageId: 3,
  candidateAnswer: '待恢复回答',
  status: status,
);

class _ReplayUiApi extends InterviewApi {
  _ReplayUiApi() : super(ApiClient());

  bool completed = false;
  int turnCalls = 0;
  int forkCalls = 0;

  @override
  Stream<TurnAttemptEvent> getTurnAttemptEvents(String turnId) {
    return const Stream.empty();
  }

  @override
  Future<TurnAttempt> getTurnAttempt(String turnId) async {
    return _attempt(turnId, 'PROCESSING');
  }

  @override
  Future<LineageTree> getLineageTree(String lineageId) async {
    return LineageTree(
      lineageId: lineageId,
      rootBranchId: 'root-1',
      focusedBranchId: 'root-1',
      nodes: [
        LineageTreeNode(
          branchId: 'root-1',
          branchLabel: '原始分支',
          stage: completed ? 'concluded' : 'project_qna',
          status: completed ? 2 : 1,
          branchVersion: 3,
          progress: completed ? 100 : 40,
          ownedAssessmentCount: 1,
          inheritedAssessmentCount: 0,
          totalAssessmentCount: 1,
          completedScore: completed ? 88 : null,
          latestBusinessActivityAt: DateTime(2026, 7, 24, 9, 30),
          evaluationSummary: '架构表达清晰',
        ),
        const LineageTreeNode(
          branchId: 'child-1',
          parentBranchId: 'root-1',
          branchLabel: '分支 1',
          forkPointMessageId: 1,
          forkTriggerMessageId: 2,
          stage: 'project_qna',
          status: 1,
          branchVersion: 1,
          progress: 42,
          ownedAssessmentCount: 1,
          inheritedAssessmentCount: 1,
          totalAssessmentCount: 2,
        ),
        const LineageTreeNode(
          branchId: 'child-2',
          parentBranchId: 'child-1',
          branchLabel: '分支 2',
          stage: 'technical_qna',
          status: 1,
          branchVersion: 1,
          progress: 55,
          ownedAssessmentCount: 1,
          inheritedAssessmentCount: 2,
          totalAssessmentCount: 3,
        ),
        const LineageTreeNode(
          branchId: 'child-3',
          parentBranchId: 'child-2',
          branchLabel: '分支 3',
          stage: 'technical_qna',
          status: 1,
          branchVersion: 1,
          progress: 60,
          ownedAssessmentCount: 1,
          inheritedAssessmentCount: 3,
          totalAssessmentCount: 4,
        ),
      ],
    );
  }

  @override
  Future<BranchTranscript> getBranchTranscript(String branchId) async {
    if (branchId == 'child-1') {
      return BranchTranscript(
        lineageId: 'lineage-1',
        branchId: 'child-1',
        branchLabel: '分支 1',
        parentBranchId: 'root-1',
        forkPointMessageId: 1,
        stage: 'project_qna',
        status: 1,
        branchVersion: 1,
        messages: const [
          BranchMessage(
            id: 1,
            owningBranchId: 'root-1',
            role: 'ai',
            messageType: 'ai_question',
            content: '根分支问题',
            stage: 'project_qna',
            sequence: 1,
            expectsResponse: true,
            deliveryStatus: 'completed',
            inherited: true,
            forkable: true,
            forkPointMessageId: 1,
            createdAt: null,
          ),
          BranchMessage(
            id: 4,
            owningBranchId: 'child-1',
            role: 'human',
            messageType: 'candidate_answer',
            content: '子分支回答',
            stage: 'project_qna',
            sequence: 1,
            expectsResponse: false,
            deliveryStatus: 'completed',
            inherited: false,
            forkable: false,
            createdAt: null,
          ),
        ],
      );
    }
    return BranchTranscript(
      lineageId: 'lineage-1',
      branchId: 'root-1',
      branchLabel: '原始分支',
      stage: completed ? 'concluded' : 'project_qna',
      status: completed ? 2 : 1,
      branchVersion: 3,
      messages: [
        const BranchMessage(
          id: 1,
          owningBranchId: 'root-1',
          role: 'ai',
          messageType: 'ai_question',
          content: '根分支问题',
          stage: 'project_qna',
          sequence: 1,
          expectsResponse: true,
          deliveryStatus: 'completed',
          inherited: false,
          forkable: true,
          forkPointMessageId: 1,
          media: [
            QuestionMedia(
              type: 'image',
              url: 'https://example.com/architecture.png',
              caption: '项目架构图',
            ),
          ],
          createdAt: null,
        ),
        BranchMessage(
          id: 2,
          owningBranchId: 'root-1',
          role: 'human',
          messageType: 'candidate_answer',
          content: completed ? '完成分支回答' : '根分支回答',
          stage: 'project_qna',
          sequence: 2,
          expectsResponse: false,
          deliveryStatus: 'completed',
          inherited: false,
          forkable: true,
          forkPointMessageId: 1,
          createdAt: null,
        ),
        BranchMessage(
          id: 3,
          owningBranchId: 'root-1',
          role: 'ai',
          messageType: completed ? 'final_summary' : 'ai_question',
          content: completed ? '面试总结' : '等待回答的问题',
          stage: completed ? 'concluded' : 'project_qna',
          sequence: 3,
          expectsResponse: !completed,
          deliveryStatus: 'completed',
          inherited: false,
          forkable: !completed,
          forkPointMessageId: completed ? null : 3,
          createdAt: null,
        ),
      ],
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
    return _attempt(turnId, 'PROCESSING');
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
}
