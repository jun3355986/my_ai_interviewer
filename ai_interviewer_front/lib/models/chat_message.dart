import 'question_media.dart';

class ChatMessage {
  final bool isAI;
  final String content;
  final String time;
  final List<QuestionMedia> media;

  ChatMessage({
    required this.isAI,
    required this.content,
    required this.time,
    this.media = const [],
  });
}
