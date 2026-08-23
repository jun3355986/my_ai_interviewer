class NotificationItem {
  const NotificationItem({
    required this.id,
    required this.userId,
    required this.type,
    required this.typeText,
    required this.title,
    required this.content,
    required this.relatedType,
    required this.relatedId,
    required this.status,
    required this.statusText,
    required this.isRead,
    required this.createdAt,
  });

  final int id;
  final int userId;
  final String type;
  final String typeText;
  final String title;
  final String content;
  final String? relatedType;
  final String? relatedId;
  final int status;
  final String statusText;
  final bool isRead;
  final DateTime? createdAt;

  factory NotificationItem.fromJson(Map<String, dynamic> json) {
    return NotificationItem(
      id: json['id'] is num ? (json['id'] as num).toInt() : 0,
      userId: json['userId'] is num ? (json['userId'] as num).toInt() : 0,
      type: json['type']?.toString() ?? '',
      typeText: json['typeText']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      content: json['content']?.toString() ?? '',
      relatedType: json['relatedType']?.toString(),
      relatedId: json['relatedId']?.toString(),
      status: json['status'] is num ? (json['status'] as num).toInt() : 0,
      statusText: json['statusText']?.toString() ?? '',
      isRead: json['isRead'] == true,
      createdAt: DateTime.tryParse('${json['createdAt']}'),
    );
  }
}

class NotificationPreference {
  const NotificationPreference({
    required this.progressNotify,
    required this.evaluationNotify,
  });

  final bool progressNotify;
  final bool evaluationNotify;

  factory NotificationPreference.fromJson(Map<String, dynamic> json) {
    return NotificationPreference(
      progressNotify: json['progressNotify'] != false,
      evaluationNotify: json['evaluationNotify'] != false,
    );
  }

  NotificationPreference copyWith({
    bool? progressNotify,
    bool? evaluationNotify,
  }) {
    return NotificationPreference(
      progressNotify: progressNotify ?? this.progressNotify,
      evaluationNotify: evaluationNotify ?? this.evaluationNotify,
    );
  }

  Map<String, dynamic> toJson() => {
    'progressNotify': progressNotify,
    'evaluationNotify': evaluationNotify,
  };
}
