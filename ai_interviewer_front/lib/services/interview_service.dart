import 'dart:async';

import 'package:flutter/material.dart';

import '../api/interview_api.dart';
import '../models/chat_message.dart';
import '../models/evaluation_report.dart';
import '../models/interview_history.dart';
import '../models/job.dart';
import 'pending_start_store.dart';

typedef TurnIdFactory = String Function();
typedef ReconnectDelay = Future<void> Function(Duration delay);

class InterviewService extends ChangeNotifier {
  InterviewService(
    this._interviewApi, {
    TurnIdFactory? turnIdFactory,
    List<Duration>? reconnectDelays,
    ReconnectDelay? reconnectDelay,
    PendingStartStore? pendingStartStore,
  }) : _turnIdFactory = turnIdFactory ?? _defaultTurnId,
       _reconnectDelays = List.unmodifiable(
         reconnectDelays ??
             const [
               Duration(milliseconds: 250),
               Duration(milliseconds: 500),
               Duration(seconds: 1),
             ],
       ),
       _reconnectDelay = reconnectDelay ?? Future<void>.delayed,
       _pendingStartStore =
           pendingStartStore ?? const SharedPreferencesPendingStartStore();

  final InterviewApi _interviewApi;
  final TurnIdFactory _turnIdFactory;
  final List<Duration> _reconnectDelays;
  final ReconnectDelay _reconnectDelay;
  final PendingStartStore _pendingStartStore;

  final List<ChatMessage> _messages = [];
  List<ChatMessage> get messages => List.unmodifiable(_messages);

  LineageTree? _tree;
  LineageTree? get tree => _tree;

  BranchTranscript? _currentTranscript;
  BranchTranscript? get currentTranscript => _currentTranscript;

  TurnAttempt? _activeAttempt;
  TurnAttempt? get activeAttempt => _activeAttempt;

  TurnAttempt? _recoveryAttempt;
  TurnAttempt? get recoveryAttempt => _recoveryAttempt;

  BranchDraft? _branchDraft;
  BranchDraft? get branchDraft => _branchDraft;
  bool get hasForkDraftForCurrentBranch =>
      _branchDraft != null &&
      _branchDraft!.focusedBranchId == _currentTranscript?.branchId;

  String _tailDraft = '';
  String get tailDraft => _tailDraft;
  String? _tailDraftBranchId;
  bool get hasTailDraftForCurrentBranch =>
      _tailDraft.isNotEmpty &&
      _tailDraftBranchId == _currentTranscript?.branchId;

  String? _conflictMessage;
  String? get conflictMessage => _conflictMessage;

  String? _replayError;
  String? get replayError => _replayError;

  bool _isLoadingReplay = false;
  bool get isLoadingReplay => _isLoadingReplay;

  String? _currentSessionId;
  String? get currentSessionId => _currentSessionId;

  EvaluationReport? _evaluationReport;
  EvaluationReport? get evaluationReport => _evaluationReport;

  int _currentStage = 1;
  int get currentStage => _currentStage;

  bool get isProcessing => _activeAttempt?.isProcessing == true;
  bool get isStreaming => isProcessing;
  bool get isCurrentBranchCompleted => _currentTranscript?.status == 2;
  bool get canReplyAtTail =>
      !isProcessing && (_currentTranscript?.canReplyAtTail ?? false);

  final Map<String, Future<void>> _attachments = {};
  final Set<String> _handledTerminalTurns = {};
  _PendingSubmission? _pendingTailSubmission;
  _PendingSubmission? _pendingForkSubmission;
  Future<StartAttempt>? _startInFlight;
  int? _startInFlightResumeId;
  int? _startInFlightJobId;

  Future<StartAttempt> startNewInterview({int? resumeId, int? jobId}) {
    final inFlight = _startInFlight;
    if (inFlight != null) {
      if (_startInFlightResumeId == resumeId && _startInFlightJobId == jobId) {
        return inFlight;
      }
      return Future.error(StateError('面试启动请求正在处理中'));
    }

    final future = _prepareAndPerformStart(resumeId: resumeId, jobId: jobId);
    _startInFlight = future;
    _startInFlightResumeId = resumeId;
    _startInFlightJobId = jobId;
    return future.whenComplete(() {
      if (identical(_startInFlight, future)) {
        _startInFlight = null;
        _startInFlightResumeId = null;
        _startInFlightJobId = null;
      }
    });
  }

  Future<StartAttempt> _prepareAndPerformStart({
    int? resumeId,
    int? jobId,
  }) async {
    final stored = await _pendingStartStore.load();
    final pending = stored?.matches(resumeId, jobId) == true
        ? stored!
        : PendingInterviewStart(
            turnId: _turnIdFactory(),
            resumeId: resumeId,
            jobId: jobId,
          );
    if (!identical(pending, stored)) {
      await _pendingStartStore.save(pending);
    }
    _resetInterviewState();
    return _performStart(pending);
  }

  Future<StartAttempt> _performStart(PendingInterviewStart pending) async {
    final started = await _interviewApi.startAttempt(
      turnId: pending.turnId,
      resumeId: pending.resumeId,
      jobId: pending.jobId,
    );
    await _pendingStartStore.clear(pending);
    _tree = LineageTree(
      lineageId: started.lineageId,
      rootBranchId: started.branchId,
      focusedBranchId: started.branchId,
      nodes: const [],
    );
    _currentSessionId = started.branchId;
    await _restoreAndAttach(started.attempt);
    return started;
  }

  Future<bool> loadReplay(String lineageId, {String? branchId}) async {
    _isLoadingReplay = true;
    _replayError = null;
    notifyListeners();
    try {
      final loadedTree = await _interviewApi.getLineageTree(lineageId);
      final targetBranchId = branchId?.isNotEmpty == true
          ? branchId!
          : loadedTree.focusedBranchId;
      final transcript = await _interviewApi.getBranchTranscript(
        targetBranchId,
      );
      _tree = loadedTree;
      hydrateTranscript(transcript);
      await _restoreTreeAttempt(targetBranchId);
      return true;
    } catch (error) {
      _replayError = _friendlyError(error);
      return false;
    } finally {
      _isLoadingReplay = false;
      notifyListeners();
    }
  }

  Future<void> selectBranch(String branchId) async {
    if (_currentTranscript?.branchId == branchId) return;
    _isLoadingReplay = true;
    _replayError = null;
    notifyListeners();
    try {
      final transcript = await _interviewApi.getBranchTranscript(branchId);
      hydrateTranscript(transcript);
      await _restoreTreeAttempt(branchId);
    } catch (error) {
      _replayError = _friendlyError(error);
    } finally {
      _isLoadingReplay = false;
      notifyListeners();
    }
  }

  Future<bool> refreshReplay() async {
    final lineageId = _tree?.lineageId ?? _currentTranscript?.lineageId;
    final branchId =
        _currentTranscript?.branchId ??
        _currentSessionId ??
        _tree?.focusedBranchId ??
        _tree?.rootBranchId;
    if (lineageId == null || branchId == null) return false;
    return loadReplay(lineageId, branchId: branchId);
  }

  Future<void> resumeInterview(String branchId) async {
    final transcript = await _interviewApi.getBranchTranscript(branchId);
    hydrateTranscript(transcript);
    _tree = await _interviewApi.getLineageTree(transcript.lineageId);
    await _restoreTreeAttempt(branchId);
    notifyListeners();
  }

  Future<InterviewLineagePage> getHistory({
    int current = 1,
    int size = 10,
    String? keyword,
    String sortBy = 'time',
    String status = 'all',
  }) {
    return _interviewApi.getLineages(
      current: current,
      size: size,
      keyword: keyword,
      sortBy: sortBy,
      status: status,
    );
  }

  Future<BranchTranscript> getBranchTranscript(String branchId) {
    return _interviewApi.getBranchTranscript(branchId);
  }

  void hydrateTranscript(BranchTranscript transcript) {
    final changedBranch =
        _currentTranscript != null &&
        _currentTranscript!.branchId != transcript.branchId;
    if (changedBranch) {
      _activeAttempt = null;
      _recoveryAttempt = null;
      _tailDraft = '';
      _tailDraftBranchId = null;
      _pendingTailSubmission = null;
      _evaluationReport = null;
    }
    _messages
      ..clear()
      ..addAll(transcript.messages.map(_chatMessageFromHistory));
    _currentSessionId = transcript.branchId;
    _currentTranscript = transcript;
    _currentStage = _stageNumber(transcript.stage);
    notifyListeners();
  }

  void restoreAttempt(TurnAttempt attempt) {
    if (attempt.status == 'DISCARDED') {
      if (_recoveryAttempt?.turnId == attempt.turnId) {
        _recoveryAttempt = null;
      }
      if (_activeAttempt?.turnId == attempt.turnId) {
        _activeAttempt = null;
      }
    } else if (attempt.isRecoverable) {
      _activeAttempt = null;
      _recoveryAttempt = attempt;
    } else if (attempt.isProcessing) {
      _activeAttempt = attempt;
      _recoveryAttempt = null;
    } else if (attempt.isCompleted) {
      _activeAttempt = null;
      _recoveryAttempt = null;
    }
    notifyListeners();
  }

  Future<void> attachToActiveAttempt() {
    final attempt = _activeAttempt;
    if (attempt == null || !attempt.isProcessing) {
      return Future.value();
    }
    final existing = _attachments[attempt.turnId];
    if (existing != null) return existing;
    final attached = _consumeAttemptEvents(attempt);
    _attachments[attempt.turnId] = attached;
    return attached.whenComplete(() {
      if (identical(_attachments[attempt.turnId], attached)) {
        _attachments.remove(attempt.turnId);
      }
    });
  }

  Future<void> submitTail(String answer) async {
    final transcript = _currentTranscript;
    final normalized = answer.trim();
    if (transcript == null || normalized.isEmpty || !canReplyAtTail) return;
    _tailDraft = answer;
    _tailDraftBranchId = transcript.branchId;
    _conflictMessage = null;
    final tailId = transcript.messages.isEmpty
        ? null
        : transcript.messages.last.id;
    final payload = _PendingSubmission(
      turnId:
          _pendingTailSubmission?.matches(
                transcript.branchId,
                normalized,
                transcript.branchVersion,
                tailId,
              ) ==
              true
          ? _pendingTailSubmission!.turnId
          : _turnIdFactory(),
      branchId: transcript.branchId,
      answer: normalized,
      branchVersion: transcript.branchVersion,
      tailMessageId: tailId,
    );
    _pendingTailSubmission = payload;
    notifyListeners();
    try {
      final attempt = await _interviewApi.createTurnAttempt(
        branchId: transcript.branchId,
        turnId: payload.turnId,
        candidateAnswer: normalized,
        expectedBranchVersion: transcript.branchVersion,
        expectedTailMessageId: tailId,
      );
      _pendingTailSubmission = null;
      _tailDraft = '';
      _tailDraftBranchId = null;
      await _restoreAndAttach(attempt);
    } catch (error) {
      _handleSubmissionError(error);
    }
  }

  Future<void> sendMessage(
    String message, {
    String? resumeId,
    String? jobId,
    bool isSystemTrigger = false,
  }) {
    return submitTail(message);
  }

  void prepareFork(BranchMessage message) {
    final transcript = _currentTranscript;
    if (transcript == null || !message.forkable) return;
    if (_branchDraft != null &&
        _branchDraft!.focusedBranchId != transcript.branchId) {
      _conflictMessage = '另一分支仍有未提交的分支草稿，请先切回处理或丢弃该草稿。';
      notifyListeners();
      return;
    }
    _branchDraft = BranchDraft(
      focusedBranchId: transcript.branchId,
      triggerMessageId: message.id,
      forkPointMessageId: message.forkPointMessageId,
      sourceMessageType: message.messageType,
      answer: message.messageType == 'candidate_answer' ? message.content : '',
      expectedFocusedBranchVersion: transcript.branchVersion,
      expectedFocusedTailMessageId: transcript.messages.isEmpty
          ? null
          : transcript.messages.last.id,
    );
    _pendingForkSubmission = null;
    _conflictMessage = null;
    notifyListeners();
  }

  void updateForkDraft(String value) {
    final draft = _branchDraft;
    if (draft == null) return;
    _branchDraft = draft.copyWith(answer: value);
    notifyListeners();
  }

  void clearForkDraft() {
    _branchDraft = null;
    _pendingForkSubmission = null;
    notifyListeners();
  }

  Future<void> submitFork() async {
    final draft = _branchDraft;
    if (draft == null || draft.answer.trim().isEmpty || isProcessing) return;
    if (!hasForkDraftForCurrentBranch) {
      _conflictMessage = '另一分支仍有未提交的分支草稿，请先切回处理或丢弃该草稿。';
      notifyListeners();
      return;
    }
    _conflictMessage = null;
    final normalized = draft.answer.trim();
    final payload = _PendingSubmission(
      turnId:
          _pendingForkSubmission?.matches(
                draft.focusedBranchId,
                normalized,
                draft.expectedFocusedBranchVersion,
                draft.expectedFocusedTailMessageId,
              ) ==
              true
          ? _pendingForkSubmission!.turnId
          : _turnIdFactory(),
      branchId: draft.focusedBranchId,
      answer: normalized,
      branchVersion: draft.expectedFocusedBranchVersion,
      tailMessageId: draft.expectedFocusedTailMessageId,
    );
    _pendingForkSubmission = payload;
    notifyListeners();
    try {
      final created = await _interviewApi.createForkAttempt(
        focusedBranchId: draft.focusedBranchId,
        turnId: payload.turnId,
        triggerMessageId: draft.triggerMessageId,
        candidateAnswer: normalized,
        expectedFocusedBranchVersion: draft.expectedFocusedBranchVersion,
        expectedFocusedTailMessageId: draft.expectedFocusedTailMessageId,
      );
      _pendingForkSubmission = null;
      _branchDraft = null;
      final lineageId = _tree?.lineageId ?? created.attempt.lineageId;
      _tree = await _interviewApi.getLineageTree(lineageId);
      hydrateTranscript(
        await _interviewApi.getBranchTranscript(created.branchId),
      );
      await _restoreAndAttach(created.attempt);
    } catch (error) {
      _handleSubmissionError(error);
    }
  }

  Future<void> retryRecovery([String? editedAnswer]) async {
    final recovery = _recoveryAttempt;
    final transcript = _currentTranscript;
    if (recovery == null || isProcessing) return;
    final answer = (editedAnswer ?? recovery.candidateAnswer).trim();
    if (answer.isEmpty) return;
    try {
      final attempt = await _interviewApi.retryTurnAttempt(
        originalTurnId: recovery.turnId,
        turnId: _turnIdFactory(),
        candidateAnswer: answer,
        expectedBranchVersion:
            transcript?.branchVersion ?? recovery.expectedBranchVersion,
        expectedTailMessageId: transcript == null
            ? recovery.expectedTailMessageId
            : transcript.messages.isEmpty
            ? null
            : transcript.messages.last.id,
      );
      await _restoreAndAttach(attempt);
    } catch (error) {
      _handleSubmissionError(error);
    }
  }

  Future<void> cancelActiveAttempt() async {
    final active = _activeAttempt;
    if (active == null || !active.isProcessing) return;
    try {
      restoreAttempt(await _interviewApi.cancelTurnAttempt(active.turnId));
    } catch (error) {
      _replayError = _friendlyError(error);
      notifyListeners();
    }
  }

  Future<void> discardRecovery() async {
    final recovery = _recoveryAttempt;
    if (recovery == null) return;
    try {
      await _interviewApi.discardTurnAttempt(recovery.turnId);
      _recoveryAttempt = null;
      notifyListeners();
      await refreshReplay();
    } catch (error) {
      _replayError = _friendlyError(error);
      notifyListeners();
    }
  }

  Future<void> _consumeAttemptEvents(TurnAttempt attempt) async {
    var reconnectIndex = 0;
    while (_isCurrentAttempt(attempt)) {
      try {
        await for (final event in _interviewApi.getTurnAttemptEvents(
          attempt.turnId,
        )) {
          if (!_isCurrentAttempt(attempt)) return;
          final updated = _withStatus(attempt, event.status);
          restoreAttempt(updated);
          if (updated.isTerminal) {
            await _handleTerminal(updated);
            return;
          }
        }
        if (!_isCurrentAttempt(attempt)) return;
        final latest = await _interviewApi.getTurnAttempt(attempt.turnId);
        if (!_isCurrentAttempt(attempt)) return;
        restoreAttempt(latest);
        if (latest.isTerminal) {
          await _handleTerminal(latest);
          return;
        }
      } catch (error) {
        if (!_isCurrentAttempt(attempt)) return;
        try {
          final latest = await _interviewApi.getTurnAttempt(attempt.turnId);
          if (!_isCurrentAttempt(attempt)) return;
          restoreAttempt(latest);
          if (latest.isTerminal) {
            await _handleTerminal(latest);
            return;
          }
        } catch (statusError) {
          _replayError = _friendlyError(statusError);
          notifyListeners();
          return;
        }
      }

      if (reconnectIndex >= _reconnectDelays.length) return;
      await _reconnectDelay(_reconnectDelays[reconnectIndex++]);
    }
  }

  bool _isCurrentAttempt(TurnAttempt attempt) {
    final current = _activeAttempt;
    return current != null &&
        current.turnId == attempt.turnId &&
        current.branchId == attempt.branchId &&
        _currentSessionId == attempt.branchId;
  }

  Future<void> _handleTerminal(TurnAttempt attempt) async {
    if (!attempt.isCompleted || _currentSessionId != attempt.branchId) return;
    if (!_handledTerminalTurns.add(attempt.turnId)) return;
    var refreshed = false;
    try {
      refreshed = await refreshReplay();
      var retryIndex = 0;
      while (!refreshed &&
          retryIndex < _reconnectDelays.length &&
          _currentSessionId == attempt.branchId) {
        await _reconnectDelay(_reconnectDelays[retryIndex++]);
        refreshed = await refreshReplay();
      }
    } finally {
      if (!refreshed) {
        _handledTerminalTurns.remove(attempt.turnId);
      }
    }
  }

  Future<MatchResult> loadResult() async {
    final branchId = _currentTranscript?.branchId ?? _currentSessionId;
    if (branchId == null || !isCurrentBranchCompleted) {
      throw StateError('面试尚未完成，不能生成评估报告');
    }
    final report = await _interviewApi.generateEvaluationReport(branchId);
    if (_currentSessionId == branchId) {
      _evaluationReport = report;
      notifyListeners();
    }
    return _matchResultFromReport(report);
  }

  Future<void> _restoreTreeAttempt(String branchId) async {
    final node = _tree?.nodes
        .where((candidate) => candidate.branchId == branchId)
        .firstOrNull;
    final turnId = node?.recoverableTurnId;
    if (turnId == null || turnId.isEmpty) return;
    final attempt = await _interviewApi.getTurnAttempt(turnId);
    await _restoreAndAttach(attempt);
  }

  Future<void> _restoreAndAttach(TurnAttempt attempt) async {
    restoreAttempt(attempt);
    if (attempt.isProcessing) {
      unawaited(attachToActiveAttempt());
    } else if (attempt.isCompleted) {
      await _handleTerminal(attempt);
    }
  }

  void _handleSubmissionError(Object error) {
    final message = _friendlyError(error);
    if (_isConflict(message)) {
      _conflictMessage = '分支状态已变化，请刷新后确认草稿再重试。';
    } else {
      _replayError = message;
    }
    notifyListeners();
  }

  bool _isConflict(String message) {
    return const [
      'BRANCH_VERSION_CONFLICT',
      'BRANCH_TAIL_CONFLICT',
      'LINEAGE_PROCESSING_CONFLICT',
      'IDEMPOTENCY',
    ].any(message.contains);
  }

  void _resetInterviewState() {
    _messages.clear();
    _tree = null;
    _currentTranscript = null;
    _activeAttempt = null;
    _recoveryAttempt = null;
    _branchDraft = null;
    _tailDraft = '';
    _tailDraftBranchId = null;
    _conflictMessage = null;
    _replayError = null;
    _currentSessionId = null;
    _evaluationReport = null;
    _currentStage = 1;
    _pendingTailSubmission = null;
    _pendingForkSubmission = null;
    notifyListeners();
  }

  TurnAttempt _withStatus(TurnAttempt source, String status) {
    return TurnAttempt(
      turnId: source.turnId,
      lineageId: source.lineageId,
      branchId: source.branchId,
      expectedBranchVersion: source.expectedBranchVersion,
      expectedTailMessageId: source.expectedTailMessageId,
      candidateAnswer: source.candidateAnswer,
      status: status,
      retryOfTurnId: source.retryOfTurnId,
      errorCode: source.errorCode,
      createdAt: source.createdAt,
      completedAt: source.completedAt,
      failedAt: source.failedAt,
      cancelledAt: source.cancelledAt,
      updatedAt: source.updatedAt,
    );
  }

  ChatMessage _chatMessageFromHistory(BranchMessage message) {
    final createdAt = message.createdAt;
    return ChatMessage(
      id: message.id,
      owningBranchId: message.owningBranchId,
      isAI: message.isAI,
      messageType: message.messageType,
      content: message.content,
      time: createdAt == null
          ? ''
          : '${createdAt.hour.toString().padLeft(2, '0')}:${createdAt.minute.toString().padLeft(2, '0')}',
      media: message.media,
      expectsResponse: message.expectsResponse,
      inherited: message.inherited,
      forkable: message.forkable,
    );
  }

  int _stageNumber(dynamic stage) {
    final normalized = stage?.toString().toLowerCase() ?? '';
    if (normalized.contains('opening')) return 1;
    if (normalized.contains('intro')) return 2;
    if (normalized.contains('project')) return 3;
    if (normalized.contains('tech')) return 4;
    if (normalized.contains('summary') ||
        normalized.contains('eval') ||
        normalized.contains('conclude')) {
      return 5;
    }
    return 1;
  }

  String _friendlyError(Object error) {
    final text = error.toString();
    return text.startsWith('Bad state: ') ? text.substring(11) : text;
  }

  MatchResult buildResult() {
    final report = _evaluationReport;
    if (report != null) {
      return _matchResultFromReport(report);
    }
    return MatchResult(
      matchScore: 0,
      matchLevel: '请查看持久化评估',
      matchDetails: const <MatchDetail>[],
      suggestions: const ['请从面试历史查看该分支的持久化评估结果。'],
    );
  }

  MatchResult _matchResultFromReport(EvaluationReport report) {
    final recommendation = report.recommendationText.isNotEmpty
        ? report.recommendationText
        : switch (report.recommendation) {
            'EXCELLENT' => '强烈推荐',
            'RECOMMEND' => '推荐',
            'CONSIDER' => '建议复试',
            'REJECT' => '暂不推荐',
            _ => '评估已完成',
          };
    return MatchResult(
      matchScore: report.overallScore.toDouble(),
      matchLevel: recommendation,
      matchDetails: [
        MatchDetail(
          category: '技术能力',
          score: report.technicalScore / 10,
          feedback: report.summary,
        ),
        MatchDetail(
          category: '沟通表达',
          score: report.communicationScore / 10,
          feedback: report.summary,
        ),
        MatchDetail(
          category: '逻辑思维',
          score: report.logicScore / 10,
          feedback: report.summary,
        ),
        MatchDetail(
          category: '项目经验',
          score: report.experienceScore / 10,
          feedback: report.summary,
        ),
      ],
      suggestions: [
        if (report.summary.isNotEmpty) '面试总结：${report.summary}',
        if (report.strengths.isNotEmpty) '优势：${report.strengths}',
        if (report.weaknesses.isNotEmpty) '改进建议：${report.weaknesses}',
      ],
    );
  }

  static int _turnSequence = 0;
  static String _defaultTurnId() {
    _turnSequence++;
    return 'flutter-${DateTime.now().microsecondsSinceEpoch}-$_turnSequence';
  }
}

class BranchDraft {
  const BranchDraft({
    required this.focusedBranchId,
    required this.triggerMessageId,
    required this.sourceMessageType,
    required this.answer,
    required this.expectedFocusedBranchVersion,
    required this.expectedFocusedTailMessageId,
    this.forkPointMessageId,
  });

  final String focusedBranchId;
  final int triggerMessageId;
  final int? forkPointMessageId;
  final String sourceMessageType;
  final String answer;
  final int expectedFocusedBranchVersion;
  final int? expectedFocusedTailMessageId;

  BranchDraft copyWith({String? answer}) {
    return BranchDraft(
      focusedBranchId: focusedBranchId,
      triggerMessageId: triggerMessageId,
      forkPointMessageId: forkPointMessageId,
      sourceMessageType: sourceMessageType,
      answer: answer ?? this.answer,
      expectedFocusedBranchVersion: expectedFocusedBranchVersion,
      expectedFocusedTailMessageId: expectedFocusedTailMessageId,
    );
  }
}

class _PendingSubmission {
  const _PendingSubmission({
    required this.turnId,
    required this.branchId,
    required this.answer,
    required this.branchVersion,
    required this.tailMessageId,
  });

  final String turnId;
  final String branchId;
  final String answer;
  final int branchVersion;
  final int? tailMessageId;

  bool matches(
    String candidateBranchId,
    String candidateAnswer,
    int candidateVersion,
    int? candidateTail,
  ) {
    return branchId == candidateBranchId &&
        answer == candidateAnswer &&
        branchVersion == candidateVersion &&
        tailMessageId == candidateTail;
  }
}
