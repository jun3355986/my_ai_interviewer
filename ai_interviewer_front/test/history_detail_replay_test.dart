import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/api/interview_api.dart';
import 'package:ai_interviewer_front/history_detail_page.dart';
import 'package:ai_interviewer_front/models/interview_history.dart';
import 'package:ai_interviewer_front/services/interview_service.dart';

void main() {
  testWidgets(
    'completed replay shows persisted transcript without a composer',
    (tester) async {
      final summary = InterviewLineageSummary(
        lineageId: 'lineage-1',
        rootSessionId: 'root-1',
        candidateName: '测试候选人',
        resumeId: 3,
        jobId: 10,
        jobTitle: 'Java 后端工程师',
        branchCount: 1,
        activeBranchCount: 0,
        completedBranchCount: 1,
        bestCompletedScore: 88,
        latestActivityAt: DateTime(2026, 7, 23, 20, 0),
        focusedBranchId: 'completed-branch',
        focusedBranchStage: 'concluded',
        focusedBranchStageDisplay: '已完成',
        focusedBranchStatus: 2,
        focusedBranchProgress: 100,
      );
      final api = _ReplayInterviewApi(
        BranchTranscript(
          lineageId: 'lineage-1',
          branchId: 'completed-branch',
          branchLabel: '原始分支',
          stage: 'concluded',
          status: 2,
          branchVersion: 8,
          messages: [
            BranchMessage(
              id: 1,
              owningBranchId: 'completed-branch',
              role: 'ai',
              messageType: 'ai_question',
              content: '持久化问题',
              stage: 'project_qna',
              sequence: 1,
              expectsResponse: false,
              deliveryStatus: 'completed',
              inherited: false,
              forkable: true,
              createdAt: DateTime(2026, 7, 23, 20, 0),
            ),
            BranchMessage(
              id: 2,
              owningBranchId: 'completed-branch',
              role: 'human',
              messageType: 'candidate_answer',
              content: '持久化回答',
              stage: 'project_qna',
              sequence: 2,
              expectsResponse: false,
              deliveryStatus: 'completed',
              inherited: false,
              forkable: true,
              createdAt: DateTime(2026, 7, 23, 20, 1),
            ),
          ],
        ),
      );

      await tester.pumpWidget(
        ChangeNotifierProvider(
          create: (_) => InterviewService(api),
          child: MaterialApp(
            onGenerateRoute: (_) => MaterialPageRoute<void>(
              settings: RouteSettings(arguments: summary),
              builder: (_) => const HistoryDetailPage(),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('面试回放'), findsOneWidget);
      expect(find.text('持久化问题'), findsOneWidget);
      expect(find.text('持久化回答'), findsOneWidget);
      expect(find.text('继续面试'), findsNothing);
      expect(find.byType(TextField), findsNothing);
      expect(api.transcriptCalls, 1);
      expect(api.chatCalls, 0);
    },
  );
}

class _ReplayInterviewApi extends InterviewApi {
  _ReplayInterviewApi(this.transcript) : super(ApiClient());

  final BranchTranscript transcript;
  int transcriptCalls = 0;
  int chatCalls = 0;

  @override
  Future<LineageTree> getLineageTree(String lineageId) async {
    return LineageTree(
      lineageId: lineageId,
      rootBranchId: transcript.branchId,
      focusedBranchId: transcript.branchId,
      nodes: const [],
    );
  }

  @override
  Future<BranchTranscript> getBranchTranscript(String branchId) async {
    transcriptCalls++;
    return transcript;
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
