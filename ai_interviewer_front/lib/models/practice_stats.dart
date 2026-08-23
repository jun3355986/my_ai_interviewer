class PracticeStats {
  const PracticeStats({
    required this.totalLineages,
    required this.activeLineages,
    required this.latestActivityAt,
    required this.dailyTrend,
  });

  final int totalLineages;
  final int activeLineages;
  final DateTime? latestActivityAt;
  final List<TrendPoint> dailyTrend;

  factory PracticeStats.fromJson(Map<String, dynamic> json) {
    final rawTrend = json['dailyTrend'];
    return PracticeStats(
      totalLineages: _asInt(json['totalLineages']),
      activeLineages: _asInt(json['activeLineages']),
      latestActivityAt: DateTime.tryParse('${json['latestActivityAt']}'),
      dailyTrend: rawTrend is List
          ? rawTrend
                .whereType<Map>()
                .map((item) => TrendPoint.fromJson(
                    Map<String, dynamic>.from(item),
                ))
                .toList()
          : const [],
    );
  }
}

class TrendPoint {
  const TrendPoint({required this.date, required this.count});

  /// yyyy-MM-dd
  final String date;
  final int count;

  factory TrendPoint.fromJson(Map<String, dynamic> json) {
    return TrendPoint(
      date: json['date']?.toString() ?? '',
      count: _asInt(json['count']),
    );
  }

  /// 显示用短日期：MM-dd
  String get shortLabel {
    if (date.length >= 10) {
      return date.substring(5);
    }
    return date;
  }
}

class EvaluationStatistics {
  const EvaluationStatistics({
    required this.totalInterviews,
    required this.completedInterviews,
    required this.averageScore,
    required this.scoreDistribution,
  });

  final int totalInterviews;
  final int completedInterviews;
  final int averageScore;
  final List<ScoreBucket> scoreDistribution;

  factory EvaluationStatistics.fromJson(Map<String, dynamic> json) {
    final rawDistribution = json['scoreDistribution'];
    return EvaluationStatistics(
      totalInterviews: _asInt(json['totalInterviews']),
      completedInterviews: _asInt(json['completedInterviews']),
      averageScore: _asInt(json['averageScore']),
      scoreDistribution: rawDistribution is List
          ? rawDistribution
                .whereType<Map>()
                .map((item) => ScoreBucket.fromJson(
                    Map<String, dynamic>.from(item),
                ))
                .toList()
          : const [],
    );
  }
}

class ScoreBucket {
  const ScoreBucket({
    required this.range,
    required this.count,
    required this.percentage,
  });

  final String range;
  final int count;
  final double percentage;

  factory ScoreBucket.fromJson(Map<String, dynamic> json) {
    return ScoreBucket(
      range: json['range']?.toString() ?? '',
      count: _asInt(json['count']),
      percentage: (json['percentage'] as num?)?.toDouble() ?? 0,
    );
  }
}

int _asInt(dynamic value) => value is num ? value.toInt() : 0;
