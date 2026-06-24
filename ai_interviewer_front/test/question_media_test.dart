import 'package:flutter_test/flutter_test.dart';
import 'package:ai_interviewer_front/models/question_media.dart';

void main() {
  test('QuestionMedia parses image payload', () {
    final media = QuestionMedia.fromJson({
      'type': ' IMAGE ',
      'url': ' https://example.com/figure.png ',
      'caption': '图 10-17',
      'alt': 'Redis 限流图',
    });

    expect(media.type, 'image');
    expect(media.url, 'https://example.com/figure.png');
    expect(media.caption, '图 10-17');
    expect(media.alt, 'Redis 限流图');
  });
}
