import 'dart:async';

import 'package:flutter/foundation.dart';

import '../api/interview_api.dart';
import '../models/chat_message.dart';
import 'interview_service.dart';

/// 模拟面试时间预算上限：超时后驱动器自动停止，会话进度保留、可稍后继续。
const Duration mockInterviewBudget = Duration(minutes: 25);

enum MockDriverStatus {
  /// 等待面试官下一个问题。
  waiting,

  /// AI 候选人生成回答中。
  answering,

  /// 面试已完整结束。
  completed,

  /// 手动停止或预算耗尽。
  stopped,

  /// 需要人工处理（本轮恢复失败 / 加载失败）。
  blocked,
}

/// 模拟面试驱动器：站在"用户打字"的位置自动作答。
///
/// 面试推进完全复用 [InterviewService] 的真实流程（turn-attempt、
/// 幂等、恢复、断线重连），驱动器只负责在 `canReplyAtTail` 时
/// 生成候选人回答并提交。
class MockAutoDriver extends ChangeNotifier {
  MockAutoDriver({
    required InterviewService interviewService,
    required InterviewApi interviewApi,
    required this.resumeId,
    this.jobId,
    this.budget = mockInterviewBudget,
    this.pollInterval = const Duration(milliseconds: 800),
  }) : _interviewService = interviewService,
       _interviewApi = interviewApi;

  final InterviewService _interviewService;
  final InterviewApi _interviewApi;
  final int resumeId;
  final int? jobId;

  /// 时间预算，默认 25 分钟；测试可注入更小值。
  final Duration budget;

  /// 状态机轮询间隔；测试可注入更小值加速。
  final Duration pollInterval;

  MockDriverStatus _status = MockDriverStatus.waiting;
  String _statusDetail = '等待面试官开场';
  DateTime? _startedAt;
  int? _lastAnsweredTailId;
  Timer? _pollTimer;
  bool _running = false;
  bool _busy = false;

  MockDriverStatus get status => _status;
  String get statusDetail => _statusDetail;
  bool get isRunning => _running;

  Duration get remainingBudget {
    final started = _startedAt;
    if (started == null) return budget;
    final remain = budget - DateTime.now().difference(started);
    return remain.isNegative ? Duration.zero : remain;
  }

  void start() {
    if (_running) return;
    _running = true;
    _startedAt ??= DateTime.now();
    // 面试状态由 InterviewService 通知；这里低频轮询状态机推进即可。
    _pollTimer = Timer.periodic(pollInterval, (_) {
      unawaited(_tick());
    });
    unawaited(_tick());
    notifyListeners();
  }

  void stop(String reason, {MockDriverStatus status = MockDriverStatus.stopped}) {
    _running = false;
    _pollTimer?.cancel();
    _pollTimer = null;
    _status = status;
    _statusDetail = reason;
    notifyListeners();
  }

  Future<void> _tick() async {
    if (!_running || _busy) return;
    final service = _interviewService;

    if (remainingBudget == Duration.zero) {
      stop('已达 ${budget.inMinutes} 分钟上限，已停止代答；面试进度已保存，可稍后继续');
      return;
    }
    if (service.isCurrentBranchCompleted) {
      stop('面试已完成，可查看评估报告', status: MockDriverStatus.completed);
      return;
    }
    if (service.recoveryAttempt != null) {
      stop('本轮需要人工恢复，已停止代答', status: MockDriverStatus.blocked);
      return;
    }
    if (service.replayError != null) {
      stop('面试状态加载失败：${service.replayError}', status: MockDriverStatus.blocked);
      return;
    }
    if (!service.canReplyAtTail) return;

    final messages = service.messages;
    if (messages.isEmpty) return;
    final tail = messages.last;
    if (!tail.isAI || tail.id == null || tail.id == _lastAnsweredTailId) return;

    _busy = true;
    _status = MockDriverStatus.answering;
    _statusDetail = 'AI 候选人生成回答中…';
    notifyListeners();
    try {
      final answer = await _interviewApi.generateMockCandidateAnswer(
        resumeId: resumeId,
        jobId: jobId,
        question: tail.content,
        questionType: _questionTypeFor(service),
        branchId: service.currentSessionId,
        recentHistory: _recentHistory(messages),
      );
      if (!_running) return; // 页面退出/手动停止后不再提交。
      _lastAnsweredTailId = tail.id;
      _status = MockDriverStatus.waiting;
      _statusDetail = '回答已提交，等待面试官…';
      notifyListeners();
      await service.submitTail(answer);
      if (!_running) return;
      final failure = service.conflictMessage ?? service.replayError;
      if (failure != null) {
        stop('提交回答后流程受阻：$failure', status: MockDriverStatus.blocked);
      }
    } catch (error) {
      if (_running) {
        stop('生成回答失败：$error', status: MockDriverStatus.blocked);
      }
    } finally {
      _busy = false;
    }
  }

  /// 用阶段号映射问题类型；追问自动跟随所属阶段。
  String _questionTypeFor(InterviewService service) {
    switch (service.currentStage) {
      case 2:
        return 'self_introduction';
      case 4:
        return 'technical';
      case 3:
      default:
        return 'project';
    }
  }

  /// 提取最近 3 组「面试官问题 → 候选人回答」，保持整场回答一致性。
  List<Map<String, String>> _recentHistory(List<ChatMessage> messages) {
    final pairs = <Map<String, String>>[];
    for (var i = 0; i < messages.length - 1; i++) {
      final question = messages[i];
      final answer = messages[i + 1];
      if (question.isAI &&
          !answer.isAI &&
          question.content.isNotEmpty &&
          answer.content.isNotEmpty) {
        pairs.add({'question': question.content, 'answer': answer.content});
      }
    }
    return pairs.length <= 3 ? pairs : pairs.sublist(pairs.length - 3);
  }

  @override
  void dispose() {
    _running = false;
    _pollTimer?.cancel();
    _pollTimer = null;
    super.dispose();
  }
}
