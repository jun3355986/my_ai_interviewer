import 'question_media.dart';

class InterviewLineagePage {
  const InterviewLineagePage({
    required this.current,
    required this.size,
    required this.total,
    required this.pages,
    required this.records,
  });

  final int current;
  final int size;
  final int total;
  final int pages;
  final List<InterviewLineageSummary> records;

  factory InterviewLineagePage.fromJson(Map<String, dynamic> json) {
    final rawRecords = json['records'];
    return InterviewLineagePage(
      current: _asInt(json['current']),
      size: _asInt(json['size']),
      total: _asInt(json['total']),
      pages: _asInt(json['pages']),
      records: rawRecords is List
          ? rawRecords
                .whereType<Map>()
                .map(
                  (record) => InterviewLineageSummary.fromJson(
                    Map<String, dynamic>.from(record),
                  ),
                )
                .toList()
          : const [],
    );
  }
}

class InterviewLineageSummary {
  const InterviewLineageSummary({
    required this.lineageId,
    required this.rootSessionId,
    required this.candidateName,
    required this.resumeId,
    required this.jobId,
    required this.jobTitle,
    required this.branchCount,
    required this.activeBranchCount,
    required this.completedBranchCount,
    required this.bestCompletedScore,
    required this.latestActivityAt,
    required this.focusedBranchId,
    required this.focusedBranchStage,
    required this.focusedBranchStageDisplay,
    required this.focusedBranchStatus,
    required this.focusedBranchProgress,
  });

  final String lineageId;
  final String rootSessionId;
  final String? candidateName;
  final int? resumeId;
  final int? jobId;
  final String? jobTitle;
  final int branchCount;
  final int activeBranchCount;
  final int completedBranchCount;
  final int? bestCompletedScore;
  final DateTime? latestActivityAt;
  final String focusedBranchId;
  final String? focusedBranchStage;
  final String? focusedBranchStageDisplay;
  final int focusedBranchStatus;
  final int focusedBranchProgress;

  bool get hasActiveBranch => focusedBranchStatus == 1;
  String get displayTitle =>
      jobTitle?.trim().isNotEmpty == true ? jobTitle!.trim() : '模拟面试';

  factory InterviewLineageSummary.fromJson(Map<String, dynamic> json) {
    return InterviewLineageSummary(
      lineageId: json['lineageId']?.toString() ?? '',
      rootSessionId: json['rootSessionId']?.toString() ?? '',
      candidateName: json['candidateName']?.toString(),
      resumeId: _asNullableInt(json['resumeId']),
      jobId: _asNullableInt(json['jobId']),
      jobTitle: json['jobTitle']?.toString(),
      branchCount: _asInt(json['branchCount']),
      activeBranchCount: _asInt(json['activeBranchCount']),
      completedBranchCount: _asInt(json['completedBranchCount']),
      bestCompletedScore: _asNullableInt(json['bestCompletedScore']),
      latestActivityAt: _asDateTime(json['latestActivityAt']),
      focusedBranchId: json['focusedBranchId']?.toString() ?? '',
      focusedBranchStage: json['focusedBranchStage']?.toString(),
      focusedBranchStageDisplay: json['focusedBranchStageDisplay']?.toString(),
      focusedBranchStatus: _asInt(json['focusedBranchStatus']),
      focusedBranchProgress: _asInt(json['focusedBranchProgress']),
    );
  }
}

class BranchTranscript {
  const BranchTranscript({
    required this.lineageId,
    required this.branchId,
    required this.branchLabel,
    required this.stage,
    required this.status,
    required this.branchVersion,
    required this.messages,
    this.parentBranchId,
    this.forkPointMessageId,
  });

  final String lineageId;
  final String branchId;
  final String? branchLabel;
  final String? parentBranchId;
  final int? forkPointMessageId;
  final String? stage;
  final int status;
  final int branchVersion;
  final List<BranchMessage> messages;

  bool get isActive => status == 1;
  bool get canReplyAtTail =>
      isActive && messages.isNotEmpty && messages.last.expectsResponse;

  factory BranchTranscript.fromJson(Map<String, dynamic> json) {
    final rawMessages = json['messages'];
    return BranchTranscript(
      lineageId: json['lineageId']?.toString() ?? '',
      branchId: json['branchId']?.toString() ?? '',
      branchLabel: json['branchLabel']?.toString(),
      parentBranchId: json['parentBranchId']?.toString(),
      forkPointMessageId: _asNullableInt(json['forkPointMessageId']),
      stage: json['stage']?.toString(),
      status: _asInt(json['status']),
      branchVersion: _asInt(json['branchVersion']),
      messages: rawMessages is List
          ? rawMessages
                .whereType<Map>()
                .map(
                  (message) => BranchMessage.fromJson(
                    Map<String, dynamic>.from(message),
                  ),
                )
                .toList()
          : const [],
    );
  }
}

class BranchMessage {
  const BranchMessage({
    required this.id,
    required this.owningBranchId,
    required this.role,
    required this.messageType,
    required this.content,
    required this.stage,
    required this.sequence,
    required this.expectsResponse,
    required this.deliveryStatus,
    required this.inherited,
    required this.forkable,
    required this.createdAt,
    this.forkPointMessageId,
    this.media = const [],
  });

  final int id;
  final String owningBranchId;
  final String role;
  final String messageType;
  final String content;
  final String? stage;
  final int sequence;
  final bool expectsResponse;
  final String deliveryStatus;
  final bool inherited;
  final bool forkable;
  final int? forkPointMessageId;
  final DateTime? createdAt;
  final List<QuestionMedia> media;

  bool get isAI => role == 'ai';

  factory BranchMessage.fromJson(Map<String, dynamic> json) {
    final metadata = json['metadata'];
    final rawMedia = metadata is Map ? metadata['media'] : null;
    return BranchMessage(
      id: _asInt(json['id']),
      owningBranchId: json['owningBranchId']?.toString() ?? '',
      role: json['role']?.toString() ?? '',
      messageType: json['messageType']?.toString() ?? '',
      content: json['content']?.toString() ?? '',
      stage: json['stage']?.toString(),
      sequence: _asInt(json['sequence']),
      expectsResponse: json['expectsResponse'] == true,
      deliveryStatus: json['deliveryStatus']?.toString() ?? '',
      inherited: json['inherited'] == true,
      forkable: json['forkable'] == true,
      forkPointMessageId: _asNullableInt(json['forkPointMessageId']),
      createdAt: _asDateTime(json['createdAt']),
      media: rawMedia is List
          ? rawMedia
                .whereType<Map>()
                .map((item) => Map<String, dynamic>.from(item))
                .where(
                  (item) =>
                      item['url'] is String &&
                      (item['url'] as String).trim().isNotEmpty,
                )
                .map(QuestionMedia.fromJson)
                .toList()
          : const [],
    );
  }
}

class LineageTree {
  const LineageTree({
    required this.lineageId,
    required this.rootBranchId,
    required this.focusedBranchId,
    required this.nodes,
  });

  final String lineageId;
  final String rootBranchId;
  final String focusedBranchId;
  final List<LineageTreeNode> nodes;

  factory LineageTree.fromJson(Map<String, dynamic> json) {
    final rawNodes = json['nodes'];
    return LineageTree(
      lineageId: json['lineageId']?.toString() ?? '',
      rootBranchId: json['rootBranchId']?.toString() ?? '',
      focusedBranchId: json['focusedBranchId']?.toString() ?? '',
      nodes: rawNodes is List
          ? rawNodes
                .whereType<Map>()
                .map(
                  (node) => LineageTreeNode.fromJson(
                    Map<String, dynamic>.from(node),
                  ),
                )
                .toList()
          : const [],
    );
  }
}

class LineageTreeNode {
  const LineageTreeNode({
    required this.branchId,
    required this.branchLabel,
    required this.stage,
    required this.status,
    required this.branchVersion,
    required this.progress,
    required this.ownedAssessmentCount,
    required this.inheritedAssessmentCount,
    required this.totalAssessmentCount,
    this.parentBranchId,
    this.forkPointMessageId,
    this.forkTriggerMessageId,
    this.latestBusinessActivityAt,
    this.completedScore,
    this.evaluationSummary,
    this.recoverableTurnId,
    this.recoverableTurnStatus,
    this.recoverableTurnErrorCode,
  });

  final String branchId;
  final String? parentBranchId;
  final String branchLabel;
  final int? forkPointMessageId;
  final int? forkTriggerMessageId;
  final String? stage;
  final int status;
  final int branchVersion;
  final DateTime? latestBusinessActivityAt;
  final int progress;
  final int ownedAssessmentCount;
  final int inheritedAssessmentCount;
  final int totalAssessmentCount;
  final int? completedScore;
  final String? evaluationSummary;
  final String? recoverableTurnId;
  final String? recoverableTurnStatus;
  final String? recoverableTurnErrorCode;

  bool get isActive => status == 1;
  bool get isCompleted => status == 2;

  factory LineageTreeNode.fromJson(Map<String, dynamic> json) {
    return LineageTreeNode(
      branchId: json['branchId']?.toString() ?? '',
      parentBranchId: json['parentBranchId']?.toString(),
      branchLabel: json['branchLabel']?.toString() ?? '原始分支',
      forkPointMessageId: _asNullableInt(json['forkPointMessageId']),
      forkTriggerMessageId: _asNullableInt(json['forkTriggerMessageId']),
      stage: json['stage']?.toString(),
      status: _asInt(json['status']),
      branchVersion: _asInt(json['branchVersion']),
      latestBusinessActivityAt: _asDateTime(json['latestBusinessActivityAt']),
      progress: _asInt(json['progress']),
      ownedAssessmentCount: _asInt(json['ownedAssessmentCount']),
      inheritedAssessmentCount: _asInt(json['inheritedAssessmentCount']),
      totalAssessmentCount: _asInt(json['totalAssessmentCount']),
      completedScore: _asNullableInt(json['completedScore']),
      evaluationSummary: json['evaluationSummary']?.toString(),
      recoverableTurnId: json['recoverableTurnId']?.toString(),
      recoverableTurnStatus: json['recoverableTurnStatus']?.toString(),
      recoverableTurnErrorCode: json['recoverableTurnErrorCode']?.toString(),
    );
  }
}

class TurnAttempt {
  const TurnAttempt({
    required this.turnId,
    required this.lineageId,
    required this.branchId,
    required this.expectedBranchVersion,
    required this.candidateAnswer,
    required this.status,
    this.expectedTailMessageId,
    this.retryOfTurnId,
    this.errorCode,
    this.createdAt,
    this.completedAt,
    this.failedAt,
    this.cancelledAt,
    this.updatedAt,
  });

  final String turnId;
  final String lineageId;
  final String branchId;
  final int expectedBranchVersion;
  final int? expectedTailMessageId;
  final String candidateAnswer;
  final String status;
  final String? retryOfTurnId;
  final String? errorCode;
  final DateTime? createdAt;
  final DateTime? completedAt;
  final DateTime? failedAt;
  final DateTime? cancelledAt;
  final DateTime? updatedAt;

  bool get isProcessing =>
      status == 'PROCESSING' || status == 'CANCEL_REQUESTED';
  bool get isCompleted => status == 'COMPLETED';
  bool get isRecoverable =>
      status == 'FAILED' || status == 'INTERRUPTED' || status == 'CANCELLED';
  bool get isTerminal => isCompleted || isRecoverable || status == 'DISCARDED';

  factory TurnAttempt.fromJson(Map<String, dynamic> json) {
    return TurnAttempt(
      turnId: json['turnId']?.toString() ?? '',
      lineageId: json['lineageId']?.toString() ?? '',
      branchId: json['branchId']?.toString() ?? '',
      expectedBranchVersion: _asInt(json['expectedBranchVersion']),
      expectedTailMessageId: _asNullableInt(json['expectedTailMessageId']),
      candidateAnswer: json['candidateAnswer']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      retryOfTurnId: json['retryOfTurnId']?.toString(),
      errorCode: json['errorCode']?.toString(),
      createdAt: _asDateTime(json['createdAt']),
      completedAt: _asDateTime(json['completedAt']),
      failedAt: _asDateTime(json['failedAt']),
      cancelledAt: _asDateTime(json['cancelledAt']),
      updatedAt: _asDateTime(json['updatedAt']),
    );
  }
}

class TurnAttemptEvent {
  const TurnAttemptEvent({
    required this.turnId,
    required this.sequence,
    required this.type,
    required this.status,
    this.occurredAt,
  });

  final String turnId;
  final int sequence;
  final String type;
  final String status;
  final DateTime? occurredAt;

  factory TurnAttemptEvent.fromJson(Map<String, dynamic> json) {
    return TurnAttemptEvent(
      turnId: json['turnId']?.toString() ?? '',
      sequence: _asInt(json['sequence']),
      type: json['type']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      occurredAt: _asDateTime(json['occurredAt']),
    );
  }
}

class ForkAttempt {
  const ForkAttempt({required this.branchId, required this.attempt});

  final String branchId;
  final TurnAttempt attempt;

  factory ForkAttempt.fromJson(Map<String, dynamic> json) {
    return ForkAttempt(
      branchId: json['branchId']?.toString() ?? '',
      attempt: TurnAttempt.fromJson(
        Map<String, dynamic>.from(json['attempt'] as Map? ?? const {}),
      ),
    );
  }
}

class StartAttempt {
  const StartAttempt({
    required this.lineageId,
    required this.branchId,
    required this.attempt,
  });

  final String lineageId;
  final String branchId;
  final TurnAttempt attempt;

  factory StartAttempt.fromJson(Map<String, dynamic> json) {
    return StartAttempt(
      lineageId: json['lineageId']?.toString() ?? '',
      branchId: json['branchId']?.toString() ?? '',
      attempt: TurnAttempt.fromJson(
        Map<String, dynamic>.from(json['attempt'] as Map? ?? const {}),
      ),
    );
  }
}

int _asInt(dynamic value) => value is num ? value.toInt() : 0;

int? _asNullableInt(dynamic value) => value is num ? value.toInt() : null;

DateTime? _asDateTime(dynamic value) {
  if (value == null) {
    return null;
  }
  return DateTime.tryParse(value.toString());
}
