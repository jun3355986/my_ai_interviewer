class EvaluationReport {
  const EvaluationReport({
    required this.sessionId,
    required this.overallScore,
    required this.technicalScore,
    required this.communicationScore,
    required this.logicScore,
    required this.experienceScore,
    required this.summary,
    required this.strengths,
    required this.weaknesses,
    required this.recommendation,
    required this.recommendationText,
    this.totalQuestions,
    this.answeredQuestions,
    this.durationMinutes,
    this.candidateName,
    this.jobTitle,
    this.createdAt,
  });

  final String sessionId;
  final int overallScore;
  final int technicalScore;
  final int communicationScore;
  final int logicScore;
  final int experienceScore;
  final String summary;
  final String strengths;
  final String weaknesses;
  final String recommendation;
  final String recommendationText;
  final int? totalQuestions;
  final int? answeredQuestions;
  final int? durationMinutes;
  final String? candidateName;
  final String? jobTitle;
  final DateTime? createdAt;

  factory EvaluationReport.fromJson(Map<String, dynamic> json) {
    int readScore(String key) {
      final value = json[key];
      return value is num ? value.round() : int.tryParse('$value') ?? 0;
    }

    int? readNullableInt(String key) =>
        json[key] is num ? (json[key] as num).toInt() : null;

    return EvaluationReport(
      sessionId: json['sessionId']?.toString() ?? '',
      overallScore: readScore('overallScore'),
      technicalScore: readScore('technicalScore'),
      communicationScore: readScore('communicationScore'),
      logicScore: readScore('logicScore'),
      experienceScore: readScore('experienceScore'),
      summary: json['summary']?.toString() ?? '',
      strengths: json['strengths']?.toString() ?? '',
      weaknesses: json['weaknesses']?.toString() ?? '',
      recommendation: json['recommendation']?.toString() ?? '',
      recommendationText: json['recommendationText']?.toString() ?? '',
      totalQuestions: readNullableInt('totalQuestions'),
      answeredQuestions: readNullableInt('answeredQuestions'),
      durationMinutes: readNullableInt('durationMinutes'),
      candidateName: json['candidateName']?.toString(),
      jobTitle: json['jobTitle']?.toString(),
      createdAt: DateTime.tryParse('${json['createdAt']}'),
    );
  }
}
