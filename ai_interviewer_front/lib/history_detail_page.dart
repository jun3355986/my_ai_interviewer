import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:provider/provider.dart';

import 'design/app_design.dart';
import 'models/interview_history.dart';
import 'services/interview_service.dart';

/// 面试回放：分支树 + 当前分支回放 + 分叉 + 尾部补答。
class HistoryDetailPage extends StatefulWidget {
  const HistoryDetailPage({super.key});

  @override
  State<HistoryDetailPage> createState() => _HistoryDetailPageState();
}

class _HistoryDetailPageState extends State<HistoryDetailPage> {
  InterviewLineageSummary? _summary;
  bool _initialized = false;
  final TextEditingController _tailController = TextEditingController();
  final TextEditingController _forkController = TextEditingController();
  final TextEditingController _recoveryController = TextEditingController();
  int? _forkTriggerId;
  String? _recoveryTurnId;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_initialized) return;
    _initialized = true;
    final arguments = ModalRoute.of(context)?.settings.arguments;
    InterviewLineageSummary? summary;
    String? lineageId;
    String? branchId;
    if (arguments is InterviewLineageSummary) {
      summary = arguments;
      lineageId = arguments.lineageId;
      branchId = arguments.focusedBranchId;
    } else if (arguments is Map) {
      lineageId = arguments['lineageId']?.toString();
      branchId = arguments['branchId']?.toString();
    }
    if (lineageId != null && lineageId.isNotEmpty) {
      _summary = summary;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        context.read<InterviewService>().loadReplay(
              lineageId!,
              branchId: branchId,
            );
      });
    }
  }

  @override
  void dispose() {
    _tailController.dispose();
    _forkController.dispose();
    _recoveryController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final narrow = MediaQuery.sizeOf(context).width < 900;
    return Scaffold(
      backgroundColor: AppColors.bg,
      appBar: AppBar(
        backgroundColor: AppColors.bg,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        leadingWidth: 64,
        leading: Padding(
          padding: const EdgeInsets.only(left: 12),
          child: Center(child: BackButtonCircle()),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('面试回放', style: TextStyle(fontSize: 17)),
            Text(
              _summary?.displayTitle ??
                  context.watch<InterviewService>().currentTranscript?.branchLabel ??
                  '面试记录',
              style: const TextStyle(fontSize: 12, color: AppColors.muted),
            ),
          ],
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: AppButton(
              label: '重新加载',
              variant: AppButtonVariant.ghost,
              small: true,
              onPressed: () =>
                  context.read<InterviewService>().refreshReplay(),
            ),
          ),
          if (narrow)
            Padding(
              padding: const EdgeInsets.only(right: 12),
              child: AppButton(
                keyOverride: const Key('open-branch-tree'),
                label: '选择分支',
                variant: AppButtonVariant.secondary,
                small: true,
                onPressed: _showBranchSheet,
              ),
            ),
        ],
      ),
      body: Consumer<InterviewService>(
        builder: (context, service, _) {
          if (_summary == null && service.currentTranscript == null) {
            return const Center(child: Text('缺少面试回放参数'));
          }
          if (service.isLoadingReplay && service.currentTranscript == null) {
            return const Center(child: CircularProgressIndicator());
          }
          if (service.replayError != null &&
              service.currentTranscript == null) {
            return _errorState(service);
          }
          if (service.currentTranscript == null) {
            return const Center(child: Text('该分支暂无回放数据'));
          }
          if (narrow) {
            return _buildTranscript(service);
          }
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                key: const Key('branch-tree-panel'),
                width: 320,
                child: _buildTree(service, inSheet: false),
              ),
              const VerticalDivider(width: 1, color: AppColors.borderSoft),
              Expanded(child: _buildTranscript(service)),
            ],
          );
        },
      ),
    );
  }

  Widget _errorState(InterviewService service) {
    return EmptyState(
      icon: Icons.cloud_off_outlined,
      title: '回放加载失败',
      message: service.replayError ?? '加载失败',
      actionLabel: '重新加载',
      onAction: service.refreshReplay,
    );
  }

  Widget _buildTree(InterviewService service, {required bool inSheet}) {
    final nodes = service.tree?.nodes ?? const <LineageTreeNode>[];
    final completedCount = nodes.where((node) => node.isCompleted).length;
    return Material(
      color: AppColors.surfaceWarm,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 18, 18, 10),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    inSheet ? '选择面试分支' : '分支树',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: AppColors.fg,
                    ),
                  ),
                ),
                AppBadge('$completedCount 个完成', tone: AppBadgeTone.success),
              ],
            ),
          ),
          Expanded(
            child: ListView.builder(
              scrollCacheExtent: const ScrollCacheExtent.pixels(5000),
              padding: const EdgeInsets.fromLTRB(10, 0, 10, 20),
              itemCount: nodes.length,
              itemBuilder: (context, index) {
                final node = nodes[index];
                final depths = _branchDepths(nodes);
                final depth = depths[node.branchId] ?? 0;
                final selected =
                    service.currentTranscript?.branchId == node.branchId;
                return Padding(
                  key: Key('branch-depth-${node.branchId}'),
                  padding: EdgeInsets.only(
                    left: depth * 18.0,
                    bottom: 8,
                  ),
                  child: InkWell(
                    key: Key('branch-node-${node.branchId}'),
                    borderRadius: BorderRadius.circular(AppRadius.md),
                    onTap: () async {
                      if (inSheet) Navigator.pop(context);
                      await service.selectBranch(node.branchId);
                    },
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: selected ? AppColors.accentSoft : AppColors.bg,
                        borderRadius: BorderRadius.circular(AppRadius.md),
                        border: Border.all(
                          color: selected
                              ? AppColors.accent
                              : AppColors.borderSoft,
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  node.branchLabel,
                                  style: TextStyle(
                                    fontSize: 13,
                                    fontWeight: FontWeight.w600,
                                    color: AppColors.fg,
                                  ),
                                ),
                              ),
                              branchStatusBadge(node.status),
                            ],
                          ),
                          const SizedBox(height: 8),
                          AppMeter(
                            value: node.progress.clamp(0, 100) / 100,
                            color: node.isCompleted ? AppColors.success : AppColors.accent,
                          ),
                          const SizedBox(height: 7),
                          Text(
                            '${node.stage ?? '未知阶段'} · ${node.progress}% · '
                            '自有 ${node.ownedAssessmentCount} / 继承 ${node.inheritedAssessmentCount}',
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.muted,
                            ),
                          ),
                          if (node.completedScore != null)
                            Text(
                              '评分 ${node.completedScore}',
                              style: const TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                                color: AppColors.success,
                              ),
                            ),
                          if (node.latestBusinessActivityAt != null)
                            Text(
                              '最新活动 ${node.latestBusinessActivityAt.relativeDisplay}',
                              style: const TextStyle(
                                fontSize: 11,
                                color: AppColors.muted,
                              ),
                            ),
                          if (node.evaluationSummary?.trim().isNotEmpty == true)
                            Text(
                              '评估：${node.evaluationSummary!.trim()}',
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 11,
                                color: AppColors.muted,
                              ),
                            ),
                          if (node.recoverableTurnStatus != null)
                            Text(
                              '恢复状态：${node.recoverableTurnStatus}',
                              style: const TextStyle(
                                fontSize: 11,
                                color: AppColors.warn,
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTranscript(InterviewService service) {
    final transcript = service.currentTranscript!;
    _syncControllers(service);
    return Container(
      key: const Key('replay-transcript'),
      color: AppColors.bg,
      child: ListView(
        scrollCacheExtent: const ScrollCacheExtent.pixels(5000),
        padding: const EdgeInsets.all(18),
        children: [
          _overview(transcript),
          if (service.conflictMessage != null) _conflictCard(service),
          if (service.replayError != null) _inlineError(service),
          ...transcript.messages.map(
            (message) => _messageCard(service, transcript, message),
          ),
          if (service.hasForkDraftForCurrentBranch) _forkDraftCard(service),
          if (service.branchDraft != null &&
              !service.hasForkDraftForCurrentBranch)
            _otherBranchDraftNotice(service),
          if (service.isProcessing) _processingCard(service),
          if (service.recoveryAttempt != null) _recoveryCard(service),
          if (service.canReplyAtTail) _tailComposer(service),
          if (transcript.status == 2 && !service.hasForkDraftForCurrentBranch)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 18),
              child: Center(
                child: Text(
                  '该分支已完成，历史内容为只读。',
                  style: TextStyle(color: AppColors.muted),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _overview(BranchTranscript transcript) {
    return AppCard(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  transcript.branchLabel ?? '原始分支',
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    color: AppColors.fg,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  '${transcript.stage ?? '未知阶段'} · 版本 ${transcript.branchVersion}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 13),
                ),
              ],
            ),
          ),
          branchStatusBadge(transcript.status),
        ],
      ),
    );
  }

  Widget _messageCard(
    InterviewService service,
    BranchTranscript transcript,
    BranchMessage message,
  ) {
    final isForkPoint = message.id == transcript.forkPointMessageId;
    final ai = message.isAI;
    return Align(
      alignment: ai ? Alignment.centerLeft : Alignment.centerRight,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 720),
        margin: const EdgeInsets.only(top: 12),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: ai ? AppColors.surface : AppColors.accentSoft,
          borderRadius: BorderRadius.circular(AppRadius.md),
          border: isForkPoint
              ? Border.all(color: AppColors.warn, width: 1.6)
              : Border.all(color: ai ? AppColors.borderSoft : AppColors.accent.withValues(alpha: 0.3)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: 6,
              children: [
                if (message.inherited) _tag('继承前缀', AppColors.meta),
                if (isForkPoint) _tag('Fork Point', AppColors.warn),
                if (!message.inherited) _tag('当前分支增量', AppColors.accent),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              message.content,
              style: const TextStyle(
                fontSize: 14,
                height: 1.5,
                color: AppColors.fg,
              ),
            ),
            if (message.media.isNotEmpty) ...[
              const SizedBox(height: 10),
              ...message.media.map(
                (media) => Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(AppRadius.sm),
                        child: Image.network(
                          media.url,
                          width: 420,
                          fit: BoxFit.contain,
                          errorBuilder: (_, _, _) => Container(
                            width: 260,
                            height: 100,
                            alignment: Alignment.center,
                            color: AppColors.surface,
                            child: const Text(
                              '图片加载失败',
                              style: TextStyle(color: AppColors.muted),
                            ),
                          ),
                        ),
                      ),
                      if (media.caption?.isNotEmpty == true) ...[
                        const SizedBox(height: 5),
                        Text(
                          media.caption!,
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppColors.muted,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ],
            if (message.forkable) ...[
              const SizedBox(height: 8),
              TextButton.icon(
                style: TextButton.styleFrom(
                  foregroundColor: AppColors.accent,
                  padding: EdgeInsets.zero,
                  minimumSize: const Size(0, 32),
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                ),
                onPressed: () => service.prepareFork(message),
                icon: const Icon(Icons.call_split, size: 15),
                label: const Text(
                  '从此处分支',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _forkDraftCard(InterviewService service) {
    return AppCard(
      key: const Key('fork-draft-card'),
      color: AppColors.warnSoft,
      border: AppColors.warn.withValues(alpha: 0.4),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '新分支草稿（尚未创建）',
            style: TextStyle(fontWeight: FontWeight.w700, color: AppColors.fg),
          ),
          const SizedBox(height: 10),
          TextField(
            key: const Key('fork-draft-field'),
            controller: _forkController,
            minLines: 2,
            maxLines: 5,
            onChanged: service.updateForkDraft,
            decoration: appInputDecoration(hint: '输入新分支的候选人回答'),
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              AppButton(
                label: '取消',
                small: true,
                variant: AppButtonVariant.ghost,
                onPressed: service.clearForkDraft,
              ),
              const SizedBox(width: 8),
              AppButton(
                keyOverride: const Key('submit-fork'),
                label: service.isProcessing ? '创建中…' : '创建分支并提交',
                small: true,
                onPressed: service.isProcessing ? null : service.submitFork,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _otherBranchDraftNotice(InterviewService service) {
    final draft = service.branchDraft!;
    return AppCard(
      key: const Key('fork-draft-other-branch'),
      color: AppColors.warnSoft,
      border: AppColors.warn.withValues(alpha: 0.4),
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          const Icon(Icons.edit_note, color: AppColors.warn, size: 22),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              '另一分支有未提交的草稿（分支 ${draft.focusedBranchId}）；切回该分支可继续编辑。',
              style: const TextStyle(fontSize: 12, color: AppColors.muted),
            ),
          ),
          TextButton(
            onPressed: service.clearForkDraft,
            child: const Text('丢弃草稿'),
          ),
        ],
      ),
    );
  }

  Widget _tailComposer(InterviewService service) {
    return AppCard(
      key: const Key('tail-composer'),
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const AppFieldLabel('回答当前分支最后一个问题'),
          const SizedBox(height: 8),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: TextField(
                  key: const Key('tail-composer-field'),
                  controller: _tailController,
                  minLines: 1,
                  maxLines: 4,
                  decoration: appInputDecoration(hint: '继续补答当前分支'),
                ),
              ),
              const SizedBox(width: 10),
              AppButton(
                keyOverride: const Key('submit-tail'),
                label: '提交',
                small: true,
                onPressed: () async {
                  final answer = _tailController.text;
                  await service.submitTail(answer);
                  if (!service.hasTailDraftForCurrentBranch) {
                    _tailController.clear();
                  }
                },
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            '提交后才会写入当前分支尾部。',
            style: TextStyle(fontSize: 12, color: AppColors.meta),
          ),
        ],
      ),
    );
  }

  Widget _processingCard(InterviewService service) {
    return AppCard(
      key: const Key('processing-card'),
      color: AppColors.accentSoft,
      border: AppColors.accent.withValues(alpha: 0.3),
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          const SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(strokeWidth: 2.2),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                Text(
                  '本轮正在后台生成',
                  style: TextStyle(fontWeight: FontWeight.w700, color: AppColors.fg),
                ),
                Text(
                  '离开页面不会取消处理；返回后会自动重新连接。',
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

  Widget _recoveryCard(InterviewService service) {
    final recovery = service.recoveryAttempt!;
    return AppCard(
      key: const Key('turn-recovery-card'),
      color: AppColors.warnSoft,
      border: AppColors.warn.withValues(alpha: 0.4),
      padding: const EdgeInsets.all(16),
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

  Widget _conflictCard(InterviewService service) {
    return AppCard(
      color: AppColors.warnSoft,
      border: AppColors.warn.withValues(alpha: 0.4),
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          const Icon(Icons.sync_problem, color: AppColors.warn, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              service.conflictMessage!,
              style: const TextStyle(fontSize: 13, color: AppColors.fg),
            ),
          ),
          TextButton(
            onPressed: service.refreshReplay,
            child: const Text('刷新'),
          ),
        ],
      ),
    );
  }

  Widget _inlineError(InterviewService service) {
    return AppCard(
      color: AppColors.dangerSoft,
      border: AppColors.danger.withValues(alpha: 0.3),
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          const Icon(Icons.error_outline, color: AppColors.danger, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              service.replayError!,
              style: const TextStyle(fontSize: 13, color: AppColors.fg),
            ),
          ),
        ],
      ),
    );
  }

  Widget _tag(String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(AppRadius.pill),
      ),
      child: Text(
        text,
        style: TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: color),
      ),
    );
  }

  Map<String, int> _branchDepths(List<LineageTreeNode> nodes) {
    const maxDepth = 12;
    final byId = {for (final node in nodes) node.branchId: node};
    return {
      for (final node in nodes)
        node.branchId: _branchDepth(node, byId, maxDepth),
    };
  }

  int _branchDepth(
    LineageTreeNode node,
    Map<String, LineageTreeNode> byId,
    int maxDepth,
  ) {
    var depth = 0;
    var current = node;
    final visited = <String>{node.branchId};
    while (depth < maxDepth) {
      final parentId = current.parentBranchId;
      if (parentId == null || !visited.add(parentId)) break;
      final parent = byId[parentId];
      if (parent == null) break;
      depth++;
      current = parent;
    }
    return depth;
  }

  void _syncControllers(InterviewService service) {
    final draft = service.branchDraft;
    if (draft != null && _forkTriggerId != draft.triggerMessageId) {
      _forkTriggerId = draft.triggerMessageId;
      _forkController.text = draft.answer;
    } else if (draft == null && _forkTriggerId != null) {
      _forkTriggerId = null;
      _forkController.clear();
    }
    final recovery = service.recoveryAttempt;
    if (recovery != null && _recoveryTurnId != recovery.turnId) {
      _recoveryTurnId = recovery.turnId;
      _recoveryController.text = recovery.candidateAnswer;
    } else if (recovery == null && _recoveryTurnId != null) {
      _recoveryTurnId = null;
      _recoveryController.clear();
    }
    if (service.hasTailDraftForCurrentBranch && _tailController.text.isEmpty) {
      _tailController.text = service.tailDraft;
    }
  }

  void _showBranchSheet() {
    final service = context.read<InterviewService>();
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: AppColors.bg,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.lg)),
      ),
      builder: (sheetContext) => SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.75,
        child: _buildTree(service, inSheet: true),
      ),
    );
  }
}
