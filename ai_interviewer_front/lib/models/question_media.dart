class QuestionMedia {
  final String type;
  final String url;
  final String? caption;
  final String? alt;

  const QuestionMedia({
    required this.type,
    required this.url,
    this.caption,
    this.alt,
  });

  factory QuestionMedia.fromJson(Map<String, dynamic> json) {
    final rawType = (json['type'] as String?)?.trim();
    return QuestionMedia(
      type: rawType == null || rawType.isEmpty ? 'image' : rawType.toLowerCase(),
      url: (json['url'] as String).trim(),
      caption: json['caption'] as String?,
      alt: json['alt'] as String?,
    );
  }
}
