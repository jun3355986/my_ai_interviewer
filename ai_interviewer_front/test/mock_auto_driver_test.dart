import 'package:flutter_test/flutter_test.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/api/interview_api.dart';
import 'package:ai_interviewer_front/models/interview_history.dart';
import 'package:ai_interviewer_front/services/interview_service.dart';
import 'package:ai_interviewer_front/services/mock_auto_driver.dart';

BranchMessage _message({
  required int id,
  required bool isAI,
  required String content,
  bool expectsResponse = false,
}) {
  return BranchMessage(
    id: id,
    owningBranchId: 'b1',
    role: isAI ? 'ai' : 'candidate',
    messageType: isAI ? 'ai_question' : 'candidate_answer',
    content: content,
    stage: 'project_qna',
    sequence: id,
    expectsResponse: expectsResponse,
    deliveryStatus: 'SENT',
    inherited: false,
    forkable: false,
    createdAt: DateTime(2026, 9, 2, 10, id),
  );
}

BranchTranscript _transcript({
  required int status,
  required List<BranchMessage> messages,
  int branchVersion = 3,
}) {
  return BranchTranscript(
    lineageId: 'L1',
    branchId: 'b1',
    branchLabel: '分支',
    stage: 'project_qna',
    status: status,
    branchVersion: branchVersion,
    messages: messages,
  );
}

class _RecordingMockApi extends InterviewApi {
  _RecordingMockApi(this.transcripts) : super(ApiClient());

  /// 按 getBranchTranscript 调用次数返回的转写序列。
  final List<BranchTranscript> transcripts;
  final List<String> answeredQuestions = [];
  final List<String> answeredTypes = [];
  final List<List<Map<String, String>>> answeredHistory = [];
  final List<String?> answeredBranchIds = [];
  final List<String> submittedAnswers = [];
  int transcriptCalls = 0;

  @override
  Future<LineageTree> getLineageTree(String lineageId) async {
    return LineageTree(
      lineageId: lineageId,
      rootBranchId: 'b1',
      focusedBranchId: 'b1',
      nodes: const [],
    );
  }

  @override
  Future<BranchTranscript> getBranchTranscript(String branchId) async {
    final index = transcriptCalls.clamp(0, transcripts.length - 1);
    transcriptCalls++;
    return transcripts[index];
  }

  @override
  Future<String> generateMockCandidateAnswer({
    required int resumeId,
    int? jobId,
    required String question,
    required String questionType,
    String? branchId,
    List<Map<String, String>> recentHistory = const [],
  }) async {
    answeredQuestions.add(question);
    answeredTypes.add(questionType);
    answeredHistory.add(recentHistory);
    answeredBranchIds.add(branchId);
    return 'AI 生成的候选人回答';
  }

  @override
  Future<TurnAttempt> createTurnAttempt({
    required String branchId,
    required String turnId,
    required String candidateAnswer,
    required int expectedBranchVersion,
    required int? expectedTailMessageId,
  }) async {
    submittedAnswers.add(candidateAnswer);
    return TurnAttempt(
      turnId: turnId,
      lineageId: 'L1',
      branchId: branchId,
      expectedBranchVersion: expectedBranchVersion,
      expectedTailMessageId: expectedTailMessageId,
      candidateAnswer: candidateAnswer,
      status: 'COMPLETED',
    );
  }
}

void main() {
  test('驱动器对尾部问题自动生成回答并在面试完成后停止', () async {
    final waiting = _transcript(
      status: 1,
      messages: [
        _message(id: 1, isAI: true, content: '第一题：请介绍项目难点'),
        _message(id: 2, isAI: false, content: '我在电商项目中解决了分布式事务问题'),
        _message(id: 3, isAI: true, content: '第二题：追问一下缓存一致性', expectsResponse: true),
      ],
    );
    final completed = _transcript(
      status: 2,
      messages: [
        _message(id: 1, isAI: true, content: '第一题：请介绍项目难点'),
        _message(id: 2, isAI: false, content: '我在电商项目中解决了分布式事务问题'),
        _message(id: 3, isAI: true, content: '第二题：追问一下缓存一致性'),
        _message(id: 4, isAI: false, content: 'AI 生成的候选人回答'),
        _message(id: 5, isAI: true, content: '面试总结'),
      ],
    );
    final api = _RecordingMockApi([completed]);
    final service = InterviewService(api);
    service.hydrateTranscript(waiting);

    final driver = MockAutoDriver(
      interviewService: service,
      interviewApi: api,
      resumeId: 7,
      jobId: 9,
      pollInterval: const Duration(milliseconds: 5),
    );
    driver.start();
    await Future<void>.delayed(const Duration(milliseconds: 120));
    driver.dispose();

    expect(api.answeredQuestions, ['第二题：追问一下缓存一致性']);
    expect(api.answeredTypes, ['project']);
    expect(api.answeredBranchIds, ['b1']);
    // 最近历史包含前一组问答，保证回答一致性。
    expect(api.answeredHistory.first, [
      {
        'question': '第一题：请介绍项目难点',
        'answer': '我在电商项目中解决了分布式事务问题',
      },
    ]);
    expect(api.submittedAnswers, ['AI 生成的候选人回答']);
    expect(driver.status, MockDriverStatus.completed);
  });

  test('预算耗尽时驱动器自动停止', () async {
    final waiting = _transcript(
      status: 1,
      messages: [
        _message(id: 1, isAI: true, content: '第一题', expectsResponse: true),
      ],
    );
    final api = _RecordingMockApi([waiting]);
    final service = InterviewService(api);
    service.hydrateTranscript(waiting);

    final driver = MockAutoDriver(
      interviewService: service,
      interviewApi: api,
      resumeId: 7,
      budget: Duration.zero,
      pollInterval: const Duration(milliseconds: 5),
    );
    driver.start();
    await Future<void>.delayed(const Duration(milliseconds: 30));
    driver.dispose();

    expect(driver.status, MockDriverStatus.stopped);
    expect(driver.statusDetail, contains('上限'));
    expect(api.answeredQuestions, isEmpty);
  });
}
