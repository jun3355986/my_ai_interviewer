import 'question_media.dart';

class ChatMessage {
  final bool isAI;
  final String content;
  final String time;
  final List<QuestionMedia> media;
  final int? id;
  final String? owningBranchId;
  final String? messageType;
  final bool expectsResponse;
  final bool inherited;
  final bool forkable;

  ChatMessage({
    required this.isAI,
    required this.content,
    required this.time,
    this.media = const [],
    this.id,
    this.owningBranchId,
    this.messageType,
    this.expectsResponse = false,
    this.inherited = false,
    this.forkable = false,
  });
}
