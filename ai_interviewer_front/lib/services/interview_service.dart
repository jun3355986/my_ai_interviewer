import 'package:flutter/material.dart';
import '../api/interview_api.dart';
import '../models/chat_message.dart';
import '../models/job.dart';
import '../models/question_media.dart';

class InterviewService extends ChangeNotifier {
  final InterviewApi _interviewApi;

  InterviewService(this._interviewApi);

  final List<ChatMessage> _messages = [];
  List<ChatMessage> get messages => List.unmodifiable(_messages);

  final List<_InterviewScoreRecord> _scoreRecords = [];

  String? _currentSessionId;
  String? get currentSessionId => _currentSessionId;

  int _currentStage = 1; // 1-Opening, 2-Intro, 3-Project, 4-Tech, 5-Summary
  int get currentStage => _currentStage;

  bool _isStreaming = false;
  bool get isStreaming => _isStreaming;

  String _currentStreamContent = '';

  /// Start a new interview
  Future<void> startNewInterview(String resumeId, String? jobId) async {
    _messages.clear();
    _scoreRecords.clear();
    _currentSessionId = null;
    _currentStage = 1;
    _currentStreamContent = '';

    // Send initial "I'm ready" message to trigger AI opening
    // Backend doc says: "首次对话传 null (sessionId), message: '我准备好了'"
    await sendMessage(
      '我准备好了',
      resumeId: resumeId,
      jobId: jobId,
      isSystemTrigger: true,
    );
  }

  /// Resume an existing interview
  Future<void> resumeInterview(String sessionId) async {
    _messages.clear();
    _currentSessionId = sessionId;
    // For now, just trigger a continuation.
    await sendMessage('继续', isSystemTrigger: true);
  }

  Future<void> sendMessage(
    String message, {
    String? resumeId,
    String? jobId,
    bool isSystemTrigger = false,
  }) async {
    if (_isStreaming) return;

    if (!isSystemTrigger) {
      _messages.add(ChatMessage(
        isAI: false,
        content: message,
        time: _getCurrentTime(),
      ));
      notifyListeners();
    }

    _isStreaming = true;
    _currentStreamContent = '';
    notifyListeners();

    try {
      await _interviewApi.chat(
        sessionId: _currentSessionId,
        message: message,
        resumeId: resumeId,
        jobId: jobId,
        onEvent: (event, data) => _handleEvent(event, data),
        onError: (e) {
          _isStreaming = false;
          _addSystemMessage('Error: $e');
          notifyListeners();
        },
        onDone: () {
          _isStreaming = false;
          notifyListeners();
        },
      );
    } catch (e) {
      _isStreaming = false;
      _addSystemMessage('Error: $e');
      notifyListeners();
    }
  }

  void _handleEvent(String event, Map<String, dynamic> data) {
    switch (event) {
      case 'status':
        if (data.containsKey('session_id') || data.containsKey('sessionId')) {
          _currentSessionId = data['session_id'] ?? data['sessionId'];
        }
        if (data.containsKey('stage')) {
          _mapStage(data['stage']);
        }
        break;
      case 'question':
        final question = data['question'];
        if (question is Map<String, dynamic>) {
          final content = question['text'] as String? ?? '';
          final rawMedia = question['media'];
          final media = rawMedia is List
              ? rawMedia
                  .whereType<Map<String, dynamic>>()
                  .map(QuestionMedia.fromJson)
                  .toList()
              : <QuestionMedia>[];

          if (content.isNotEmpty) {
            _messages.add(ChatMessage(
              isAI: true,
              content: content,
              time: _getCurrentTime(),
              media: media,
            ));
            _currentStreamContent = content;
            notifyListeners();
          }
        }
        break;
      case 'chunk':
        final content = data['content'] as String? ?? '';
        if (_messages.isNotEmpty &&
            _messages.last.isAI &&
            _messages.last.media.isNotEmpty &&
            _currentStreamContent == _messages.last.content &&
            content.isNotEmpty &&
            _messages.last.content.contains(content)) {
          break;
        }
        _currentStreamContent += content;

        if (_messages.isEmpty || !_messages.last.isAI) {
          _messages.add(ChatMessage(
            isAI: true,
            content: _currentStreamContent,
            time: _getCurrentTime(),
          ));
        } else {
          _messages.removeLast();
          _messages.add(ChatMessage(
            isAI: true,
            content: _currentStreamContent,
            time: _getCurrentTime(),
          ));
        }
        notifyListeners();
        break;
      case 'done':
        if (data.containsKey('stage')) {
          _mapStage(data['stage']);
        }
        _isStreaming = false;
        notifyListeners();
        break;
      case 'score':
        _recordScore(data);
        notifyListeners();
        break;
      case 'result':
        if (data.containsKey('next_stage')) {
          _mapStage(data['next_stage']);
        }
        break;
      case 'error':
        _addSystemMessage(_normalizeSystemError(data['message'] ?? 'Unknown error'));
        _isStreaming = false;
        notifyListeners();
        break;
    }
  }

  String _normalizeSystemError(dynamic raw) {
    final message = raw?.toString() ?? 'Unknown error';
    final normalized = message.toLowerCase();
    final isAuthError = normalized.contains('error code: 401') ||
        normalized.contains('authentication fails') ||
        normalized.contains('authentication_error') ||
        normalized.contains('invalid_request_error');

    if (isAuthError) {
      return 'AI 服务鉴权失败，请检查 DEEPSEEK_API_KEY 配置后重试。';
    }
    return message;
  }

  void _mapStage(dynamic stage) {
    if (stage is int) {
      _currentStage = stage;
    } else if (stage is String) {
      final s = stage.toLowerCase();
      if (s.contains('opening')) {
        _currentStage = 1;
      } else if (s.contains('intro')) {
        _currentStage = 2;
      } else if (s.contains('project')) {
        _currentStage = 3;
      } else if (s.contains('tech')) {
        _currentStage = 4;
      } else if (s.contains('summary') ||
          s.contains('eval') ||
          s.contains('conclude')) {
        _currentStage = 5;
      }
    }
    notifyListeners();
  }

  MatchResult buildResult() {
    if (_scoreRecords.isEmpty) {
      return MatchResult(
        matchScore: 0,
        matchLevel: '暂无评分',
        matchDetails: const <MatchDetail>[],
        suggestions: const ['建议：本次面试暂无评分记录，请返回查看详细对话确认是否已完成答题。'],
      );
    }

    final averageScore = _scoreRecords
            .map((record) => record.score)
            .reduce((left, right) => left + right) /
        _scoreRecords.length;
    final groupedScores = <String, List<_InterviewScoreRecord>>{};
    for (final record in _scoreRecords) {
      groupedScores.putIfAbsent(record.stageLabel, () => []).add(record);
    }

    final details = groupedScores.entries.map((entry) {
      final stageAverage = entry.value
              .map((record) => record.score)
              .reduce((left, right) => left + right) /
          entry.value.length;
      return MatchDetail(
        category: entry.key,
        score: stageAverage / 10,
        feedback: entry.value.map((record) => record.feedback).join('\n'),
      );
    }).toList();

    final strongest = _scoreRecords.reduce(
      (best, current) => current.score > best.score ? current : best,
    );
    final weakest = _scoreRecords.reduce(
      (weakest, current) => current.score < weakest.score ? current : weakest,
    );

    return MatchResult(
      matchScore: averageScore,
      matchLevel: _matchLevel(averageScore),
      matchDetails: details,
      suggestions: [
        '优点：${strongest.stageLabel}表现相对较好，最高得分为${strongest.score}分。${strongest.feedback}',
        '建议：${weakest.stageLabel}仍有提升空间，最低得分为${weakest.score}分。${weakest.feedback}',
      ],
    );
  }

  void _recordScore(Map<String, dynamic> data) {
    final rawScore = data['score'];
    if (rawScore is! num) {
      return;
    }
    final feedback = data['feedback']?.toString().trim();
    _scoreRecords.add(
      _InterviewScoreRecord(
        score: rawScore.toInt().clamp(0, 100).toInt(),
        feedback:
            feedback == null || feedback.isEmpty ? '暂无详细反馈。' : feedback,
        stageLabel: _stageLabel(_currentStage),
      ),
    );
  }

  String _stageLabel(int stage) {
    switch (stage) {
      case 3:
        return '项目经验';
      case 4:
        return '技术问答';
      default:
        return '面试表现';
    }
  }

  String _matchLevel(double score) {
    if (score >= 85) {
      return '优秀';
    }
    if (score >= 70) {
      return '良好';
    }
    if (score >= 60) {
      return '合格';
    }
    return '待提升';
  }

  void _addSystemMessage(String content) {
    _messages.add(ChatMessage(
      isAI: true,
      content: '[System] $content',
      time: _getCurrentTime(),
    ));
  }

  String _getCurrentTime() {
    final now = DateTime.now();
    return '${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}';
  }
}

class _InterviewScoreRecord {
  final int score;
  final String feedback;
  final String stageLabel;

  const _InterviewScoreRecord({
    required this.score,
    required this.feedback,
    required this.stageLabel,
  });
}
