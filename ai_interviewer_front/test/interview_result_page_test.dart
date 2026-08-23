import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/api/interview_api.dart';
import 'package:ai_interviewer_front/interview_result_page.dart';
import 'package:ai_interviewer_front/models/evaluation_report.dart';
import 'package:ai_interviewer_front/models/interview_history.dart';
import 'package:ai_interviewer_front/services/interview_service.dart';

void main() {
  testWidgets(
    'completed branch renders the persisted evaluation instead of a zero fallback',
    (tester) async {
      final api = _EvaluationInterviewApi();
      final service = InterviewService(api)
        ..hydrateTranscript(
          const BranchTranscript(
            lineageId: 'lineage-1',
            branchId: 'completed-branch',
            branchLabel: '主分支',
            stage: 'concluded',
            status: 2,
            branchVersion: 12,
            messages: [],
          ),
        );

      await tester.pumpWidget(
        ChangeNotifierProvider.value(
          value: service,
          child: const MaterialApp(home: InterviewResultPage()),
        ),
      );
      await tester.pumpAndSettle();
      await tester.pump(const Duration(milliseconds: 1500));

      expect(api.generatedBranchIds, ['completed-branch']);
      expect(find.text('82'), findsOneWidget);
      expect(find.text('技术能力'), findsOneWidget);
      expect(find.text('面试总结：项目与技术问题回答完整。'), findsOneWidget);
      expect(find.text('优势：扎实的技术基础'), findsOneWidget);
      expect(find.text('改进建议：回答可以更具体'), findsOneWidget);
    },
  );
}

class _EvaluationInterviewApi extends InterviewApi {
  _EvaluationInterviewApi() : super(ApiClient());

  final List<String> generatedBranchIds = [];

  @override
  Future<EvaluationReport?> getEvaluationReport(String branchId) async => null;

  @override
  Future<EvaluationReport> generateEvaluationReport(String branchId) async {
    generatedBranchIds.add(branchId);
    return EvaluationReport(
      sessionId: branchId,
      overallScore: 82,
      technicalScore: 86,
      communicationScore: 80,
      logicScore: 84,
      experienceScore: 78,
      summary: '项目与技术问题回答完整。',
      strengths: '扎实的技术基础',
      weaknesses: '回答可以更具体',
      recommendation: 'RECOMMEND',
      recommendationText: '推荐',
    );
  }
}
