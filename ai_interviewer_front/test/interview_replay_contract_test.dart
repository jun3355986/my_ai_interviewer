import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/api/interview_api.dart';
import 'package:ai_interviewer_front/models/interview_history.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('replay models parse tree, fork point, attempts, events and responses', () {
    final tree = LineageTree.fromJson({
      'lineageId': 'lineage-1',
      'rootBranchId': 'root-1',
      'focusedBranchId': 'child-1',
      'nodes': [
        {
          'branchId': 'child-1',
          'parentBranchId': 'root-1',
          'branchLabel': '分支 1',
          'forkPointMessageId': 7,
          'forkTriggerMessageId': 8,
          'stage': 'project_qna',
          'status': 1,
          'branchVersion': 3,
          'progress': 40,
          'ownedAssessmentCount': 1,
          'inheritedAssessmentCount': 2,
          'totalAssessmentCount': 3,
          'completedScore': 88,
          'evaluationSummary': '稳定',
          'recoverableTurnId': 'turn-1',
          'recoverableTurnStatus': 'FAILED',
          'recoverableTurnErrorCode': 'MODEL_PROCESSING_FAILED',
          'latestBusinessActivityAt': '2026-07-24T10:00:00',
        },
      ],
    });
    final message = BranchMessage.fromJson({
      'id': 8,
      'owningBranchId': 'root-1',
      'role': 'human',
      'messageType': 'candidate_answer',
      'content': '原回答',
      'sequence': 2,
      'expectsResponse': false,
      'deliveryStatus': 'completed',
      'inherited': true,
      'forkable': true,
      'forkPointMessageId': 7,
    });
    final attempt = TurnAttempt.fromJson({
      'turnId': 'turn-1',
      'lineageId': 'lineage-1',
      'branchId': 'child-1',
      'expectedBranchVersion': 3,
      'expectedTailMessageId': 8,
      'candidateAnswer': 'edited',
      'status': 'FAILED',
      'retryOfTurnId': 'turn-0',
      'errorCode': 'MODEL_PROCESSING_FAILED',
    });
    final event = TurnAttemptEvent.fromJson({
      'turnId': 'turn-1',
      'sequence': 2,
      'type': 'failed',
      'status': 'FAILED',
      'occurredAt': '2026-07-24T10:01:00',
    });
    final fork = ForkAttempt.fromJson({
      'branchId': 'child-1',
      'attempt': {'turnId': 'turn-1', 'status': 'PROCESSING'},
    });
    final start = StartAttempt.fromJson({
      'lineageId': 'lineage-2',
      'branchId': 'root-2',
      'attempt': {'turnId': 'opening-1', 'status': 'PROCESSING'},
    });

    expect(tree.nodes.single.branchLabel, '分支 1');
    expect(tree.nodes.single.totalAssessmentCount, 3);
    expect(message.forkPointMessageId, 7);
    expect(attempt.isRecoverable, isTrue);
    expect(event.status, 'FAILED');
    expect(fork.attempt.turnId, 'turn-1');
    expect(start.branchId, 'root-2');
  });

  test('durable API uses authenticated service Dio paths and exact payloads', () async {
    SharedPreferences.setMockInitialValues(const {});
    final requests = <RequestOptions>[];
    final dio = Dio(BaseOptions(baseUrl: 'http://api.example.test'));
    dio.httpClientAdapter = _RecordingAdapter(requests);
    final api = InterviewApi(ApiClient(dio: dio));

    await api.startAttempt(turnId: 'start-1', resumeId: 20, jobId: 10);
    await api.getLineageTree('lineage-1');
    await api.createTurnAttempt(
      branchId: 'branch-1',
      turnId: 'turn-1',
      candidateAnswer: 'answer',
      expectedBranchVersion: 4,
      expectedTailMessageId: 9,
    );
    await api.createForkAttempt(
      focusedBranchId: 'branch-1',
      turnId: 'fork-1',
      triggerMessageId: 8,
      candidateAnswer: 'edited',
      expectedFocusedBranchVersion: 4,
      expectedFocusedTailMessageId: 9,
    );
    await api.getTurnAttempt('turn-1');
    final event = await api.getTurnAttemptEvents('turn-1').first;
    await api.retryTurnAttempt(
      originalTurnId: 'turn-1',
      turnId: 'retry-1',
      candidateAnswer: 'edited again',
      expectedBranchVersion: 5,
      expectedTailMessageId: 11,
    );
    await api.cancelTurnAttempt('turn-1');
    await api.discardTurnAttempt('turn-1');

    expect(event.status, 'PROCESSING');
    expect(requests.map((request) => request.path), [
      '/api/v1/interviews/start-attempts',
      '/api/v1/interviews/lineages/lineage-1/tree',
      '/api/v1/interviews/branches/branch-1/turn-attempts',
      '/api/v1/interviews/branches/branch-1/fork-attempts',
      '/api/v1/interviews/turn-attempts/turn-1',
      '/api/v1/interviews/turn-attempts/turn-1/events',
      '/api/v1/interviews/turn-attempts/turn-1/retry',
      '/api/v1/interviews/turn-attempts/turn-1/cancel',
      '/api/v1/interviews/turn-attempts/turn-1/discard',
    ]);
    expect(requests[0].data, {'turnId': 'start-1', 'resumeId': 20, 'jobId': 10});
    expect(requests[2].data, {
      'turnId': 'turn-1',
      'candidateAnswer': 'answer',
      'expectedBranchVersion': 4,
      'expectedTailMessageId': 9,
    });
    expect(requests[3].data, {
      'turnId': 'fork-1',
      'triggerMessageId': 8,
      'candidateAnswer': 'edited',
      'expectedFocusedBranchVersion': 4,
      'expectedFocusedTailMessageId': 9,
    });
    expect(requests[6].data, {
      'turnId': 'retry-1',
      'candidateAnswer': 'edited again',
      'expectedBranchVersion': 5,
      'expectedTailMessageId': 11,
    });
  });
}

class _RecordingAdapter implements HttpClientAdapter {
  _RecordingAdapter(this.requests);

  final List<RequestOptions> requests;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    if (options.path.endsWith('/events')) {
      return ResponseBody.fromString(
        'event: snapshot\ndata: ${jsonEncode({'turnId': 'turn-1', 'sequence': 0, 'type': 'snapshot', 'status': 'PROCESSING'})}\n\n',
        200,
        headers: const {
          'content-type': ['text/event-stream'],
        },
      );
    }
    final data = switch (options.path) {
      String path when path.endsWith('/tree') => {
        'lineageId': 'lineage-1',
        'rootBranchId': 'root-1',
        'focusedBranchId': 'branch-1',
        'nodes': <dynamic>[],
      },
      String path when path.endsWith('/fork-attempts') => {
        'branchId': 'child-1',
        'attempt': {'turnId': 'fork-1', 'status': 'PROCESSING'},
      },
      String path when path.endsWith('/start-attempts') => {
        'lineageId': 'lineage-1',
        'branchId': 'root-1',
        'attempt': {'turnId': 'start-1', 'status': 'PROCESSING'},
      },
      _ => {'turnId': 'turn-1', 'status': 'PROCESSING'},
    };
    return ResponseBody.fromString(
      jsonEncode({'code': 200, 'data': data}),
      200,
      headers: const {
        'content-type': ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
