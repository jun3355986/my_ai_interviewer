import 'package:flutter_test/flutter_test.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/api/interview_api.dart';
import 'package:ai_interviewer_front/models/interview_history.dart';
import 'package:ai_interviewer_front/models/question_media.dart';
import 'package:ai_interviewer_front/services/interview_service.dart';

void main() {
  test('branch message restores structured media from persisted metadata', () {
    final message = BranchMessage.fromJson({
      'id': 7,
      'owningBranchId': 'branch-1',
      'role': 'ai',
      'messageType': 'ai_question',
      'content': '请查看架构图。',
      'sequence': 1,
      'expectsResponse': true,
      'deliveryStatus': 'completed',
      'inherited': false,
      'forkable': true,
      'metadata': {
        'media': [
          {
            'type': 'image',
            'url': 'https://example.com/project.png',
            'caption': '项目架构图',
          },
        ],
      },
    });

    expect(message.media, hasLength(1));
    expect(message.media.single.url, 'https://example.com/project.png');
    expect(message.media.single.caption, '项目架构图');
  });

  test(
    'resume hydrates the persisted branch without sending a continue chat',
    () async {
      final api = _FakeInterviewApi(
        BranchTranscript(
          lineageId: 'lineage-1',
          branchId: 'branch-1',
          branchLabel: '原始分支',
          stage: 'project_qna',
          status: 1,
          branchVersion: 4,
          messages: [
            BranchMessage(
              id: 1,
              owningBranchId: 'branch-1',
              role: 'ai',
              messageType: 'ai_question',
              content: '请介绍你最有挑战的项目。',
              stage: 'project_qna',
              sequence: 1,
              expectsResponse: true,
              deliveryStatus: 'completed',
              inherited: false,
              forkable: true,
              media: const [
                QuestionMedia(
                  type: 'image',
                  url: 'https://example.com/project.png',
                  caption: '项目架构图',
                ),
              ],
              createdAt: DateTime(2026, 7, 23, 20, 0),
            ),
          ],
        ),
      );
      final service = InterviewService(api);

      await service.resumeInterview('branch-1');

      expect(api.transcriptCalls, 1);
      expect(api.chatCalls, 0);
      expect(service.currentSessionId, 'branch-1');
      expect(service.messages, hasLength(1));
      expect(service.messages.single.content, '请介绍你最有挑战的项目。');
      expect(
        service.messages.single.media.single.url,
        'https://example.com/project.png',
      );
      expect(service.canReplyAtTail, isTrue);
    },
  );
}

class _FakeInterviewApi extends InterviewApi {
  _FakeInterviewApi(this.transcript) : super(ApiClient());

  final BranchTranscript transcript;
  int transcriptCalls = 0;
  int treeCalls = 0;
  int chatCalls = 0;

  @override
  Future<LineageTree> getLineageTree(String lineageId) async {
    treeCalls++;
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
