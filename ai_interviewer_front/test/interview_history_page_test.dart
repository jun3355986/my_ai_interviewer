import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/api/interview_api.dart';
import 'package:ai_interviewer_front/interview_history_page.dart';
import 'package:ai_interviewer_front/models/interview_history.dart';
import 'package:ai_interviewer_front/services/interview_service.dart';

void main() {
  testWidgets(
    'history page renders persisted lineage summaries instead of fixtures',
    (tester) async {
      final api = _HistoryInterviewApi(
        InterviewLineagePage(
          current: 1,
          size: 10,
          total: 1,
          pages: 2,
          records: [
            InterviewLineageSummary(
              lineageId: 'lineage-1',
              rootSessionId: 'root-1',
              candidateName: '测试候选人',
              resumeId: 3,
              jobId: 10,
              jobTitle: 'Java 后端工程师',
              branchCount: 2,
              activeBranchCount: 1,
              completedBranchCount: 1,
              bestCompletedScore: 88,
              latestActivityAt: DateTime(2026, 7, 23, 20, 0),
              focusedBranchId: 'active-branch',
              focusedBranchStage: 'project_qna',
              focusedBranchStageDisplay: '项目提问',
              focusedBranchStatus: 1,
              focusedBranchProgress: 36,
            ),
          ],
        ),
      );

      await tester.pumpWidget(
        ChangeNotifierProvider(
          create: (_) => InterviewService(api),
          child: const MaterialApp(home: InterviewHistoryPage()),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Java 后端工程师'), findsOneWidget);
      expect(find.text('测试候选人'), findsOneWidget);
      expect(find.text('面试回放'), findsOneWidget);
      expect(find.text('继续面试'), findsOneWidget);
      expect(find.text('前端开发工程师'), findsNothing);
      expect(api.historyCalls, 1);
      expect(api.statuses, ['all']);

      await tester.tap(find.byKey(const Key('history-status-active')));
      await tester.pumpAndSettle();
      expect(api.statuses, ['all', 'active']);

      await tester.enterText(find.byType(TextField), '候选人');
      await tester.tap(find.byTooltip('搜索'));
      await tester.pumpAndSettle();
      expect(api.lastKeyword, '候选人');
      expect(api.statuses.last, 'active');

      await tester.tap(find.text('按最佳评分'));
      await tester.pumpAndSettle();
      expect(api.lastSortBy, 'score');
      expect(api.statuses.last, 'active');

      await tester.drag(find.byType(ListView), const Offset(0, -500));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('下一页'));
      await tester.pumpAndSettle();
      expect(api.lastCurrent, 2);
      expect(api.statuses.last, 'active');
    },
  );
}

class _HistoryInterviewApi extends InterviewApi {
  _HistoryInterviewApi(this.page) : super(ApiClient());

  final InterviewLineagePage page;
  int historyCalls = 0;
  int? lastCurrent;
  String? lastKeyword;
  String? lastSortBy;
  final List<String> statuses = [];

  @override
  Future<InterviewLineagePage> getLineages({
    int current = 1,
    int size = 10,
    String? keyword,
    String sortBy = 'time',
    String status = 'all',
  }) async {
    historyCalls++;
    lastCurrent = current;
    lastKeyword = keyword;
    lastSortBy = sortBy;
    statuses.add(status);
    return page;
  }
}
