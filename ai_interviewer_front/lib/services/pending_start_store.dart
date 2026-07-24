import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

class PendingInterviewStart {
  const PendingInterviewStart({
    required this.turnId,
    required this.resumeId,
    required this.jobId,
  });

  final String turnId;
  final int? resumeId;
  final int? jobId;

  bool matches(int? candidateResumeId, int? candidateJobId) {
    return resumeId == candidateResumeId && jobId == candidateJobId;
  }

  @override
  bool operator ==(Object other) {
    return other is PendingInterviewStart &&
        other.turnId == turnId &&
        other.resumeId == resumeId &&
        other.jobId == jobId;
  }

  @override
  int get hashCode => Object.hash(turnId, resumeId, jobId);
}

abstract interface class PendingStartStore {
  Future<PendingInterviewStart?> load();

  Future<void> save(PendingInterviewStart pending);

  Future<void> clear(PendingInterviewStart expected);
}

class SharedPreferencesPendingStartStore implements PendingStartStore {
  const SharedPreferencesPendingStartStore();

  static const storageKey = 'interview.pending_start.v1';

  @override
  Future<PendingInterviewStart?> load() async {
    final preferences = await SharedPreferences.getInstance();
    final encoded = preferences.getString(storageKey);
    if (encoded == null) return null;
    try {
      final decoded = jsonDecode(encoded);
      if (decoded is! Map) return null;
      final turnId = decoded['turnId'];
      final resumeId = decoded['resumeId'];
      final jobId = decoded['jobId'];
      if (turnId is! String || turnId.isEmpty) return null;
      if (resumeId != null && resumeId is! int) return null;
      if (jobId != null && jobId is! int) return null;
      return PendingInterviewStart(
        turnId: turnId,
        resumeId: resumeId as int?,
        jobId: jobId as int?,
      );
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> save(PendingInterviewStart pending) async {
    final preferences = await SharedPreferences.getInstance();
    final saved = await preferences.setString(
      storageKey,
      jsonEncode({
        'turnId': pending.turnId,
        'resumeId': pending.resumeId,
        'jobId': pending.jobId,
      }),
    );
    if (!saved) throw StateError('无法保存面试启动请求，请重试');
  }

  @override
  Future<void> clear(PendingInterviewStart expected) async {
    final preferences = await SharedPreferences.getInstance();
    final current = await load();
    if (current == expected) {
      final removed = await preferences.remove(storageKey);
      if (!removed) throw StateError('无法清除已完成的面试启动请求');
    }
  }

  Future<void> clearAll() async {
    final preferences = await SharedPreferences.getInstance();
    if (!preferences.containsKey(storageKey)) return;
    final removed = await preferences.remove(storageKey);
    if (!removed) {
      throw StateError('无法清除当前账号的面试启动请求');
    }
  }
}
