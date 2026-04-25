class Job {
  final String? id;
  final String title;
  final String? company;
  final String? location;
  final String? salaryDisplay;
  final String? description;
  final List<String>? skills;
  final String? statusText;

  Job({
    this.id,
    required this.title,
    this.company,
    this.location,
    this.salaryDisplay,
    this.description,
    this.skills,
    this.statusText,
  });

  factory Job.fromJson(Map<String, dynamic> json) => Job(
    id: json['id'] as String?,
    title: json['title'] as String,
    company: json['company'] as String?,
    location: json['location'] as String?,
    salaryDisplay: json['salaryDisplay'] as String?,
    description: json['description'] as String?,
    skills: (json['skills'] as List<dynamic>?)
        ?.map((e) => e as String)
        .toList(),
    statusText: json['statusText'] as String?,
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'company': company,
    'location': location,
    'salaryDisplay': salaryDisplay,
    'description': description,
    'skills': skills,
    'statusText': statusText,
  };
}

class MatchResult {
  final double matchScore;
  final String matchLevel;
  final List<MatchDetail>? matchDetails;
  final List<String>? suggestions;

  MatchResult({
    required this.matchScore,
    required this.matchLevel,
    this.matchDetails,
    this.suggestions,
  });

  factory MatchResult.fromJson(Map<String, dynamic> json) => MatchResult(
    matchScore: (json['matchScore'] as num).toDouble(),
    matchLevel: json['matchLevel'] as String,
    matchDetails: (json['matchDetails'] as List<dynamic>?)
        ?.map((e) => MatchDetail.fromJson(e as Map<String, dynamic>))
        .toList(),
    suggestions: (json['suggestions'] as List<dynamic>?)
        ?.map((e) => e as String)
        .toList(),
  );

  Map<String, dynamic> toJson() => {
    'matchScore': matchScore,
    'matchLevel': matchLevel,
    'matchDetails': matchDetails?.map((e) => e.toJson()).toList(),
    'suggestions': suggestions,
  };
}

class MatchDetail {
  final String category;
  final double score;
  final String feedback;

  MatchDetail({
    required this.category,
    required this.score,
    required this.feedback,
  });

  factory MatchDetail.fromJson(Map<String, dynamic> json) => MatchDetail(
    category: json['category'] as String,
    score: (json['score'] as num).toDouble(),
    feedback: json['feedback'] as String,
  );

  Map<String, dynamic> toJson() => {
    'category': category,
    'score': score,
    'feedback': feedback,
  };
}
