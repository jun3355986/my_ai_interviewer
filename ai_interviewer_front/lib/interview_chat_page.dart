import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'design/app_design.dart';
import 'models/chat_message.dart';
import 'models/interview_history.dart';
import 'models/question_media.dart';
import 'services/interview_service.dart';

/// 面试工作台：大纲 / 对话 / 观察 三区联动，durable 流程不变。
class InterviewChatPage extends StatefulWidget {
  const InterviewChatPage({super.key});

  @override
  State<InterviewChatPage> createState() => _InterviewChatPageState();
}

enum _WorkspacePane { outline, conversation, insights }

class _InterviewChatPageState extends State<InterviewChatPage> {
  final TextEditingController _messageController = TextEditingController();
  final TextEditingController _recoveryController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  String? _recoveryTurnId;
  Timer? _ticker;

  static const List<String> _stages = ['开场', '自我介绍', '项目经验', '技术问答', '总结'];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      context.read<InterviewService>().attachToActiveAttempt();
    });
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _messageController.dispose();
    _recoveryController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final interviewService = context.watch<InterviewService>();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });

    return Scaffold(
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: Column(
          children: [
            _buildTopBar(interviewService),
            Expanded(
              child: LayoutBuilder(
                builder: (context, constraints) {
                  final wide = constraints.maxWidth >= 1020;
                  if (wide) {
                    return Row(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        SizedBox(
                          width: 250,
                          child: _buildOutlinePanel(interviewService),
                        ),
                        const VerticalDivider(width: 1, color: AppColors.borderSoft),
                        Expanded(child: _buildConversationPanel(interviewService)),
                        const VerticalDivider(width: 1, color: AppColors.borderSoft),
                        SizedBox(
                          width: 290,
                          child: _buildInsightsPanel(interviewService),
                        ),
                      ],
                    );
                  }
                  return _buildMobileWorkspace(interviewService);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTopBar(InterviewService service) {
    final transcript = service.currentTranscript;
    final answered = _answeredQuestionCount(service);
    final elapsed = _elapsedDuration(transcript);
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.bg,
        border: Border(bottom: BorderSide(color: AppColors.borderSoft)),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      child: Row(
        children: [
          BackButtonCircle(onTap: () => _showExitDialog(context)),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '面试工作台',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: AppColors.fg,
                  ),
                ),
                Text(
                  '${_stageName(service)} · 已进行 ${_formatDuration(elapsed)} · 问题 $answered',
                  style: const TextStyle(fontSize: 12, color: AppColors.muted),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          AppButton(
            label: '退出面试',
            variant: AppButtonVariant.danger,
            small: true,
            onPressed: () => _showExitDialog(context),
          ),
          const SizedBox(width: 8),
          AppButton(
            label: '结束并生成报告',
            variant: AppButtonVariant.secondary,
            small: true,
            onPressed: () => _finishInterview(service),
          ),
        ],
      ),
    );
  }

  String _stageName(InterviewService service) {
    return _stages[(service.currentStage - 1).clamp(0, 4)];
  }

  int _answeredQuestionCount(InterviewService service) {
    return service.messages
        .where((message) => !message.isAI && message.id != null)
        .length;
  }

  Duration _elapsedDuration(BranchTranscript? transcript) {
    final messages = transcript?.messages ?? const <BranchMessage>[];
    DateTime? start;
    for (final message in messages) {
      if (message.createdAt != null) {
        start = message.createdAt;
        break;
      }
    }
    if (start == null) return Duration.zero;
    return DateTime.now().difference(start);
  }

  String _formatDuration(Duration duration) {
    final minutes = duration.inMinutes;
    final seconds = duration.inSeconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }

  // ───────────── 大纲面板 ─────────────

  Widget _buildOutlinePanel(InterviewService service) {
    final completed = service.isCurrentBranchCompleted;
    return Container(
      color: AppColors.bg,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const UserAvatar(size: 38),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      service.currentTranscript?.branchLabel ?? '模拟面试',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: AppColors.fg,
                      ),
                    ),
                    Text(
                      _stageName(service),
                      style: const TextStyle(fontSize: 11, color: AppColors.muted),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          completed
              ? const AppBadge('已完成', tone: AppBadgeTone.success)
              : const AppBadge('进行中', tone: AppBadgeTone.accent),
          const SizedBox(height: 18),
          Expanded(
            child: ListView(
              physics: const NeverScrollableScrollPhysics(),
              children: [
                for (var index = 0; index < _stages.length; index++)
                  _buildStageItem(service, index),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStageItem(InterviewService service, int index) {
    final stageNumber = index + 1;
    final isCompleted = stageNumber < service.currentStage;
    final isCurrent = stageNumber == service.currentStage;
    final color = isCompleted
        ? AppColors.success
        : isCurrent
        ? AppColors.accent
        : AppColors.border;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        children: [
          Container(
            width: 28,
            height: 28,
            decoration: BoxDecoration(
              color: isCompleted || isCurrent ? color : Colors.transparent,
              border: Border.all(color: color),
              shape: BoxShape.circle,
            ),
            alignment: Alignment.center,
            child: isCompleted
                ? const Icon(Icons.check, size: 14, color: Colors.white)
                : Text(
                    '$stageNumber',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: isCurrent ? Colors.white : AppColors.muted,
                    ),
                  ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _stages[index],
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: isCurrent ? FontWeight.w600 : FontWeight.w500,
                    color: isCurrent
                        ? AppColors.accent
                        : isCompleted
                        ? AppColors.fg
                        : AppColors.muted,
                  ),
                ),
                Text(
                  isCompleted
                      ? '已完成'
                      : isCurrent
                      ? '当前阶段'
                      : '未开始',
                  style: const TextStyle(fontSize: 11, color: AppColors.meta),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ───────────── 对话面板 ─────────────

  Widget _buildConversationPanel(InterviewService service) {
    return Container(
      color: AppColors.bg,
      child: Column(
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            decoration: const BoxDecoration(
              border: Border(bottom: BorderSide(color: AppColors.borderSoft)),
            ),
            child: Text(
              '对话 · ${_questionCount(service)} 个问题',
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: AppColors.fg,
              ),
            ),
          ),
          Expanded(child: _buildChatArea(service)),
          _buildInputArea(service),
        ],
      ),
    );
  }

  int _questionCount(InterviewService service) {
    return service.messages.where((message) => message.isAI).length;
  }

  Widget _buildChatArea(InterviewService service) {
    _syncRecoveryController(service);
    return ListView(
      controller: _scrollController,
      padding: const EdgeInsets.all(18),
      children: [
        ...service.messages.map(_buildMessageBubble),
        if (service.isProcessing) _buildProcessingCard(service),
        if (service.recoveryAttempt != null) _buildRecoveryCard(service),
        if (service.messages.isEmpty &&
            !service.isProcessing &&
            service.recoveryAttempt == null)
          if (service.replayError != null)
            _buildReplayErrorCard(service)
          else
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(
                child: Text(
                  '正在加载持久化面试记录...',
                  style: TextStyle(color: AppColors.muted),
                ),
              ),
            ),
      ],
    );
  }

  Widget _buildReplayErrorCard(InterviewService service) {
    return Container(
      key: const Key('chat-replay-error'),
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.warnSoft,
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          const Icon(Icons.sync_problem, color: AppColors.warn, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '持久化面试记录加载失败',
                  style: TextStyle(fontWeight: FontWeight.w700, color: AppColors.fg),
                ),
                Text(
                  service.replayError ?? '请稍后重试',
                  style: const TextStyle(fontSize: 12, color: AppColors.muted),
                ),
              ],
            ),
          ),
          TextButton(
            key: const Key('chat-replay-retry'),
            onPressed: service.refreshReplay,
            child: const Text('重新加载'),
          ),
        ],
      ),
    );
  }

  Widget _buildProcessingCard(InterviewService service) {
    return Container(
      key: const Key('chat-processing-card'),
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.accentSoft,
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          const SizedBox.square(
            dimension: 20,
            child: CircularProgressIndicator(strokeWidth: 2.2),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                Text(
                  '本轮正在后台生成',
                  style: TextStyle(fontWeight: FontWeight.w700, color: AppColors.fg),
                ),
                Text(
                  '可立即退出；后台处理不会因离开页面取消。',
                  style: TextStyle(fontSize: 12, color: AppColors.muted),
                ),
              ],
            ),
          ),
          TextButton(
            onPressed: service.cancelActiveAttempt,
            child: const Text('取消本轮'),
          ),
        ],
      ),
    );
  }

  Widget _buildRecoveryCard(InterviewService service) {
    final recovery = service.recoveryAttempt!;
    return Container(
      key: const Key('chat-recovery-card'),
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.warnSoft,
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '本轮需要恢复：${recovery.status}',
            style: const TextStyle(fontWeight: FontWeight.w700, color: AppColors.fg),
          ),
          if (recovery.errorCode != null)
            Text(
              '错误代码：${recovery.errorCode}',
              style: const TextStyle(fontSize: 12, color: AppColors.muted),
            ),
          const SizedBox(height: 10),
          TextField(
            key: const Key('chat-recovery-field'),
            controller: _recoveryController,
            minLines: 2,
            maxLines: 4,
            decoration: appInputDecoration(),
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              AppButton(
                label: '重试本轮',
                small: true,
                onPressed: () =>
                    service.retryRecovery(_recoveryController.text),
              ),
              const SizedBox(width: 8),
              AppButton(
                label: '丢弃本轮',
                small: true,
                variant: AppButtonVariant.secondary,
                onPressed: service.discardRecovery,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildMessageBubble(ChatMessage message) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: message.isAI
            ? CrossAxisAlignment.start
            : CrossAxisAlignment.end,
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: 4, left: 4, right: 4),
            child: Text(
              message.isAI ? '面试助手' : '我',
              style: const TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: AppColors.muted,
              ),
            ),
          ),
          Row(
            mainAxisAlignment: message.isAI
                ? MainAxisAlignment.start
                : MainAxisAlignment.end,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Flexible(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 15,
                    vertical: 13,
                  ),
                  decoration: BoxDecoration(
                    color: message.isAI
                        ? AppColors.surface
                        : AppColors.accent,
                    border: message.isAI
                        ? Border.all(color: AppColors.borderSoft)
                        : null,
                    borderRadius: message.isAI
                        ? const BorderRadius.only(
                            topLeft: Radius.circular(4),
                            topRight: Radius.circular(16),
                            bottomLeft: Radius.circular(16),
                            bottomRight: Radius.circular(16),
                          )
                        : const BorderRadius.only(
                            topLeft: Radius.circular(16),
                            topRight: Radius.circular(4),
                            bottomLeft: Radius.circular(16),
                            bottomRight: Radius.circular(16),
                          ),
                    boxShadow: message.isAI
                        ? null
                        : AppShadows.accentButton,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        message.content,
                        style: TextStyle(
                          fontSize: 14,
                          height: 1.5,
                          color: message.isAI ? AppColors.fg : Colors.white,
                        ),
                      ),
                      if (message.media.isNotEmpty) ...[
                        const SizedBox(height: 10),
                        ...message.media.map((media) => _buildMediaCard(media)),
                      ],
                    ],
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildMediaCard(QuestionMedia media) {
    if (media.type != 'image') {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: GestureDetector(
        onTap: () => _showImagePreview(context, media),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(AppRadius.sm),
              child: Image.network(
                media.url,
                fit: BoxFit.cover,
                height: 160,
                width: 260,
                errorBuilder: (_, _, _) => Container(
                  height: 100,
                  width: 260,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.circular(AppRadius.sm),
                  ),
                  child: const Text(
                    '图片加载失败，请稍后重试',
                    style: TextStyle(color: AppColors.muted, fontSize: 12),
                  ),
                ),
              ),
            ),
            if (media.caption?.isNotEmpty == true) ...[
              const SizedBox(height: 5),
              Text(
                media.caption!,
                style: const TextStyle(fontSize: 12, color: AppColors.muted),
              ),
            ],
          ],
        ),
      ),
    );
  }

  void _showImagePreview(BuildContext context, QuestionMedia media) {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => Dialog(
        insetPadding: const EdgeInsets.all(16),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.lg),
        ),
        child: InteractiveViewer(
          child: Image.network(
            media.url,
            fit: BoxFit.contain,
            errorBuilder: (_, _, _) => const Padding(
              padding: EdgeInsets.all(32),
              child: Text('图片加载失败，请稍后重试'),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildInputArea(InterviewService service) {
    if (service.isCurrentBranchCompleted) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        decoration: const BoxDecoration(
          color: AppColors.surface,
          border: Border(top: BorderSide(color: AppColors.borderSoft)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              '该面试分支已结束，可回放历史对话或查看持久化评估报告。',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 13, color: AppColors.muted),
            ),
            const SizedBox(height: 10),
            AppButton(
              keyOverride: const Key('view-evaluation-report'),
              label: '查看评估报告',
              onPressed: () => Navigator.pushNamed(context, '/result'),
            ),
          ],
        ),
      );
    }
    if (service.isProcessing) {
      return _buildReadOnlyNotice(
        icon: Icons.hourglass_top,
        text: '本轮正在后台生成，等待持久化结果后才能继续。',
      );
    }
    if (service.recoveryAttempt != null) {
      return _buildReadOnlyNotice(
        icon: Icons.warning_amber_rounded,
        text: '请先重试或丢弃失败的本轮。',
      );
    }
    if (!service.canReplyAtTail) {
      return _buildReadOnlyNotice(
        icon: Icons.hourglass_top,
        text: service.currentTranscript == null
            ? service.replayError == null
                  ? '正在加载持久化面试记录，暂时不能提交。'
                  : '持久化面试记录加载失败，请先重新加载。'
            : '当前没有等待回答的问题，请返回回放页刷新状态。',
      );
    }
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(top: BorderSide(color: AppColors.borderSoft)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          GestureDetector(
            onTap: () => showAppToast(context, '语音输入尚未接入'),
            child: Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: Colors.transparent,
                shape: BoxShape.circle,
                border: Border.all(color: AppColors.border),
              ),
              child: const Icon(Icons.mic_none_rounded, size: 20, color: AppColors.muted),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: TextField(
              key: const Key('chat-message-field'),
              controller: _messageController,
              enabled: !service.isStreaming,
              minLines: 1,
              maxLines: 5,
              keyboardType: TextInputType.multiline,
              textInputAction: TextInputAction.newline,
              decoration: appInputDecoration(
                hint: service.isStreaming ? '对方正在输入...' : '输入你的回答',
              ),
            ),
          ),
          const SizedBox(width: 10),
          AppButton(
            keyOverride: const Key('chat-send'),
            label: '提交回答',
            small: true,
            onPressed: service.isStreaming ? null : () => _sendMessage(service),
          ),
        ],
      ),
    );
  }

  Widget _buildReadOnlyNotice({required IconData icon, required String text}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(top: BorderSide(color: AppColors.borderSoft)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 18, color: AppColors.muted),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              text,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 13, color: AppColors.muted),
            ),
          ),
        ],
      ),
    );
  }

  // ───────────── 观察面板 ─────────────

  Widget _buildInsightsPanel(InterviewService service) {
    final transcript = service.currentTranscript;
    final node = _currentNode(service);
    final answered = _answeredQuestionCount(service);
    final progress = node?.progress ?? 0;
    return Container(
      color: AppColors.bg,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '观察',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: AppColors.fg,
            ),
          ),
          const SizedBox(height: 16),
          _buildInsightMetric(
            label: '阶段进度',
            value: '$progress%',
            ratio: progress / 100,
          ),
          _buildInsightMetric(
            label: '已回答问题',
            value: '$answered',
            ratio: answered >= 10 ? 1 : answered / 10,
            color: AppColors.success,
          ),
          _buildInsightMetric(
            label: '分支版本',
            value: 'v${transcript?.branchVersion ?? 0}',
            ratio: ((transcript?.branchVersion ?? 0) % 10) / 10,
            color: AppColors.warn,
          ),
          const Spacer(),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(AppRadius.sm),
              border: Border.all(color: AppColors.borderSoft),
            ),
            child: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '面试官备注',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: AppColors.fg,
                  ),
                ),
                SizedBox(height: 6),
                Text(
                  '该面试分支结束后，可回放历史对话或查看持久化评估报告。',
                  style: TextStyle(fontSize: 12, color: AppColors.muted, height: 1.5),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  LineageTreeNode? _currentNode(InterviewService service) {
    final branchId = service.currentTranscript?.branchId;
    final nodes = service.tree?.nodes;
    if (branchId == null || nodes == null) return null;
    for (final node in nodes) {
      if (node.branchId == branchId) return node;
    }
    return null;
  }

  Widget _buildInsightMetric({
    required String label,
    required String value,
    required double ratio,
    Color? color,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                label,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.fg2,
                ),
              ),
              Text(
                value,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.muted,
                ),
              ),
            ],
          ),
          const SizedBox(height: 7),
          AppMeter(value: ratio, color: color),
        ],
      ),
    );
  }

  // ───────────── 移动端面板切换 ─────────────

  Widget _buildMobileWorkspace(InterviewService service) {
    return Column(
      children: [
        ValueListenableBuilder<_WorkspacePane>(
          valueListenable: _paneNotifier,
          builder: (context, pane, _) => Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
            child: AppSegmentedControl<_WorkspacePane>(
              segments: const [
                (_WorkspacePane.outline, '大纲'),
                (_WorkspacePane.conversation, '对话'),
                (_WorkspacePane.insights, '观察'),
              ],
              selected: pane,
              onChanged: (value) => _paneNotifier.value = value,
            ),
          ),
        ),
        Expanded(
          child: ValueListenableBuilder<_WorkspacePane>(
            valueListenable: _paneNotifier,
            builder: (context, pane, _) => switch (pane) {
              _WorkspacePane.outline => _buildOutlinePanel(service),
              _WorkspacePane.insights => _buildInsightsPanel(service),
              _WorkspacePane.conversation => _buildConversationPanel(service),
            },
          ),
        ),
      ],
    );
  }

  final ValueNotifier<_WorkspacePane> _paneNotifier =
      ValueNotifier(_WorkspacePane.conversation);

  Future<void> _sendMessage(InterviewService service) async {
    final text = _messageController.text.trim();
    if (text.isEmpty) {
      showAppToast(context, '请先输入回答');
      return;
    }
    if (service.isStreaming) return;

    await service.sendMessage(text);
    if (service.hasTailDraftForCurrentBranch) {
      _messageController.text = service.tailDraft;
      _messageController.selection = TextSelection.collapsed(
        offset: _messageController.text.length,
      );
    } else {
      _messageController.clear();
    }
  }

  void _syncRecoveryController(InterviewService service) {
    final recovery = service.recoveryAttempt;
    if (recovery != null && recovery.turnId != _recoveryTurnId) {
      _recoveryTurnId = recovery.turnId;
      _recoveryController.text = recovery.candidateAnswer;
    } else if (recovery == null && _recoveryTurnId != null) {
      _recoveryTurnId = null;
      _recoveryController.clear();
    }
  }

  Future<void> _finishInterview(InterviewService service) async {
    if (!service.isCurrentBranchCompleted) {
      showAppToast(context, '当前分支尚未完成，完成后才能生成评估报告');
      return;
    }
    Navigator.pushNamed(context, '/result');
  }

  void _showExitDialog(BuildContext context) {
    final isStreaming = context.read<InterviewService>().isStreaming;
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.lg),
        ),
        title: const Text('退出面试'),
        content: Text(
          isStreaming
              ? '此前已提交的进度已保存，但本轮结果尚未确认提交。退出后请稍后从面试历史刷新，确定退出吗？'
              : '当前已提交的面试进度已保存，可稍后从面试历史继续。确定退出吗？',
          style: const TextStyle(color: AppColors.muted, height: 1.5),
        ),
        actions: [
          AppButton(
            label: '继续面试',
            small: true,
            variant: AppButtonVariant.secondary,
            onPressed: () => Navigator.pop(dialogContext),
          ),
          AppButton(
            label: '确定退出',
            small: true,
            variant: AppButtonVariant.danger,
            onPressed: () {
              Navigator.pop(dialogContext);
              Navigator.pushNamedAndRemoveUntil(
                context,
                '/home',
                (route) => false,
              );
            },
          ),
        ],
      ),
    );
  }
}
