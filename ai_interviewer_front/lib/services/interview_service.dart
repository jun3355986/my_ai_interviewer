import 'package:flutter/material.dart';
import '../api/interview_api.dart';
import '../models/chat_message.dart';

class InterviewService extends ChangeNotifier {
  final InterviewApi _interviewApi;

  InterviewService(this._interviewApi);

  final List<ChatMessage> _messages = [];
  List<ChatMessage> get messages => List.unmodifiable(_messages);

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
    _currentSessionId = null;
    _currentStage = 1;
    _currentStreamContent = '';
    
    // Send initial "I'm ready" message to trigger AI opening
    // Backend doc says: "首次对话传 null (sessionId), message: '我准备好了'"
    await sendMessage('我准备好了', resumeId: resumeId, jobId: jobId, isSystemTrigger: true);
  }

  /// Resume an existing interview
  Future<void> resumeInterview(String sessionId) async {
     _messages.clear();
     _currentSessionId = sessionId;
     // For now, just trigger a continuation
     // Or we could fetch history if the API supported it
     // Let's try sending an empty message or "continue"
     await sendMessage('继续', isSystemTrigger: true);
  }

  Future<void> sendMessage(String message, {String? resumeId, String? jobId, bool isSystemTrigger = false}) async {
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
      case 'chunk':
        final content = data['content'] as String? ?? '';
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
        _isStreaming = false;
        notifyListeners();
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
      } else if (s.contains('summary') || s.contains('eval') || s.contains('conclude')) {
        _currentStage = 5;
      }
    }
    notifyListeners();
  }
  
  void _addSystemMessage(String content) {
      _messages.add(ChatMessage(
          isAI: true, 
          content: '[System] $content',
          time: _getCurrentTime()
      ));
  }

  String _getCurrentTime() {
    final now = DateTime.now();
    return '${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}';
  }
}
