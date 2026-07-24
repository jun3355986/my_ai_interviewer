import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'models/interview_history.dart';
import 'services/interview_service.dart';

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
    if (arguments is InterviewLineageSummary) {
      _summary = arguments;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        context.read<InterviewService>().loadReplay(
          arguments.lineageId,
          branchId: arguments.focusedBranchId,
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
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.white,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('面试回放', style: TextStyle(fontSize: 17)),
            Text(
              _summary?.displayTitle ?? '面试记录',
              style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
            ),
          ],
        ),
        actions: [
          if (narrow)
            IconButton(
              key: const Key('open-branch-tree'),
              tooltip: '打开分支树',
              onPressed: _showBranchSheet,
              icon: const Icon(Icons.account_tree_outlined),
            ),
        ],
      ),
      body: Consumer<InterviewService>(
        builder: (context, service, _) {
          if (_summary == null) {
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
            children: [
              SizedBox(
                key: const Key('branch-tree-panel'),
                width: 320,
                child: _buildTree(service, inSheet: false),
              ),
              const VerticalDivider(width: 1),
              Expanded(child: _buildTranscript(service)),
            ],
          );
        },
      ),
    );
  }

  Widget _errorState(InterviewService service) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(service.replayError ?? '加载失败'),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: service.refreshReplay,
            child: const Text('重新加载'),
          ),
        ],
      ),
    );
  }

  Widget _buildTree(InterviewService service, {required bool inSheet}) {
    final nodes = service.tree?.nodes ?? const <LineageTreeNode>[];
    final depths = _branchDepths(nodes);
    return Material(
      color: Colors.white,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 18, 18, 10),
            child: Text(
              inSheet ? '选择面试分支' : '分支树',
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
            ),
          ),
          Expanded(
            child: ListView.builder(
              cacheExtent: 5000,
              padding: const EdgeInsets.fromLTRB(10, 0, 10, 20),
              itemCount: nodes.length,
              itemBuilder: (context, index) {
                final node = nodes[index];
                final selected =
                    service.currentTranscript?.branchId == node.branchId;
                return Padding(
                  key: Key('branch-depth-${node.branchId}'),
                  padding: EdgeInsets.only(
                    left: (depths[node.branchId] ?? 0) * 18.0,
                    bottom: 8,
                  ),
                  child: InkWell(
                    key: Key('branch-node-${node.branchId}'),
                    borderRadius: BorderRadius.circular(12),
                    onTap: () async {
                      if (inSheet) Navigator.pop(context);
                      await service.selectBranch(node.branchId);
                    },
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: selected
                            ? const Color(0xFFEFF6FF)
                            : const Color(0xFFF8FAFC),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: selected
                              ? const Color(0xFF3B82F6)
                              : const Color(0xFFE2E8F0),
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
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                              _statusBadge(node.status),
                            ],
                          ),
                          const SizedBox(height: 7),
                          LinearProgressIndicator(
                            value: node.progress.clamp(0, 100) / 100,
                            minHeight: 5,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          const SizedBox(height: 7),
                          Text(
                            '${node.stage ?? '未知阶段'} · ${node.progress}% · '
                            '自有 ${node.ownedAssessmentCount} / 继承 ${node.inheritedAssessmentCount}',
                            style: const TextStyle(
                              fontSize: 11,
                              color: Color(0xFF64748B),
                            ),
                          ),
                          if (node.completedScore != null)
                            Text(
                              '评分 ${node.completedScore}',
                              style: const TextStyle(
                                fontSize: 12,
                                color: Color(0xFF15803D),
                              ),
                            ),
                          if (node.latestBusinessActivityAt != null)
                            Text(
                              '最新活动 ${_compactDateTime(node.latestBusinessActivityAt!)}',
                              style: const TextStyle(
                                fontSize: 11,
                                color: Color(0xFF475569),
                              ),
                            ),
                          if (node.evaluationSummary?.trim().isNotEmpty == true)
                            Text(
                              '评估：${node.evaluationSummary!.trim()}',
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 11,
                                color: Color(0xFF475569),
                              ),
                            ),
                          if (node.recoverableTurnStatus != null)
                            Text(
                              '恢复状态：${node.recoverableTurnStatus}',
                              style: const TextStyle(
                                fontSize: 11,
                                color: Color(0xFFB45309),
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
      color: const Color(0xFFF8FAFC),
      child: ListView(
        cacheExtent: 5000,
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
              child: Center(child: Text('该分支已完成，历史内容为只读。')),
            ),
        ],
      ),
    );
  }

  Widget _overview(BranchTranscript transcript) {
    return Card(
      margin: const EdgeInsets.only(bottom: 14),
      child: Padding(
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
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    '${transcript.stage ?? '未知阶段'} · 版本 ${transcript.branchVersion}',
                    style: const TextStyle(color: Color(0xFF64748B)),
                  ),
                ],
              ),
            ),
            _statusBadge(transcript.status),
          ],
        ),
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
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: ai ? Colors.white : const Color(0xFF2563EB),
          borderRadius: BorderRadius.circular(14),
          border: isForkPoint
              ? Border.all(color: const Color(0xFFF59E0B), width: 2)
              : Border.all(
                  color: ai ? const Color(0xFFE2E8F0) : Colors.transparent,
                ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: 6,
              children: [
                if (message.inherited) _tag('继承前缀', const Color(0xFF7C3AED)),
                if (isForkPoint) _tag('Fork Point', const Color(0xFFD97706)),
                if (!message.inherited) _tag('当前分支增量', const Color(0xFF0284C7)),
              ],
            ),
            const SizedBox(height: 7),
            Text(
              message.content,
              style: TextStyle(
                height: 1.45,
                color: ai ? const Color(0xFF0F172A) : Colors.white,
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
                        borderRadius: BorderRadius.circular(10),
                        child: Image.network(
                          media.url,
                          width: 420,
                          fit: BoxFit.contain,
                          errorBuilder: (_, _, _) => Container(
                            width: 260,
                            height: 100,
                            alignment: Alignment.center,
                            color: const Color(0xFFF1F5F9),
                            child: const Text('图片加载失败'),
                          ),
                        ),
                      ),
                      if (media.caption?.isNotEmpty == true) ...[
                        const SizedBox(height: 5),
                        Text(
                          media.caption!,
                          style: TextStyle(
                            fontSize: 12,
                            color: ai
                                ? const Color(0xFF64748B)
                                : Colors.white70,
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
                  foregroundColor: ai ? const Color(0xFF2563EB) : Colors.white,
                  padding: EdgeInsets.zero,
                ),
                onPressed: () => service.prepareFork(message),
                icon: const Icon(Icons.call_split, size: 16),
                label: const Text('从此处分支'),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _forkDraftCard(InterviewService service) {
    return Card(
      color: const Color(0xFFFFFBEB),
      margin: const EdgeInsets.only(top: 6, bottom: 14),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '新分支草稿（尚未创建）',
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 10),
            TextField(
              key: const Key('fork-draft-field'),
              controller: _forkController,
              minLines: 2,
              maxLines: 5,
              onChanged: service.updateForkDraft,
              decoration: const InputDecoration(
                hintText: '输入新分支的候选人回答',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: service.clearForkDraft,
                  child: const Text('取消'),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  key: const Key('submit-fork'),
                  onPressed: service.isProcessing ? null : service.submitFork,
                  child: const Text('创建分支并提交'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _otherBranchDraftNotice(InterviewService service) {
    final draft = service.branchDraft!;
    return Card(
      key: const Key('fork-draft-other-branch'),
      color: const Color(0xFFFFFBEB),
      margin: const EdgeInsets.only(top: 6, bottom: 14),
      child: ListTile(
        leading: const Icon(Icons.edit_note, color: Color(0xFFD97706)),
        title: const Text('另一分支有未提交的草稿'),
        subtitle: Text('草稿属于分支 ${draft.focusedBranchId}；切回该分支可继续编辑。'),
        trailing: TextButton(
          onPressed: service.clearForkDraft,
          child: const Text('丢弃草稿'),
        ),
      ),
    );
  }

  Widget _tailComposer(InterviewService service) {
    return Card(
      key: const Key('tail-composer'),
      margin: const EdgeInsets.only(top: 8),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            Expanded(
              child: TextField(
                key: const Key('tail-composer-field'),
                controller: _tailController,
                minLines: 1,
                maxLines: 4,
                decoration: const InputDecoration(
                  hintText: '回答当前分支最后一个问题',
                  border: OutlineInputBorder(),
                ),
              ),
            ),
            const SizedBox(width: 10),
            FilledButton.icon(
              key: const Key('submit-tail'),
              onPressed: () async {
                final answer = _tailController.text;
                await service.submitTail(answer);
                if (!service.hasTailDraftForCurrentBranch) {
                  _tailController.clear();
                }
              },
              icon: const Icon(Icons.send),
              label: const Text('提交'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _processingCard(InterviewService service) {
    return Card(
      key: const Key('processing-card'),
      color: const Color(0xFFEFF6FF),
      margin: const EdgeInsets.only(top: 8, bottom: 12),
      child: ListTile(
        leading: const SizedBox(
          width: 22,
          height: 22,
          child: CircularProgressIndicator(strokeWidth: 2.5),
        ),
        title: const Text('本轮正在后台生成'),
        subtitle: const Text('离开页面不会取消处理；返回后会自动重新连接。'),
        trailing: TextButton(
          onPressed: service.cancelActiveAttempt,
          child: const Text('取消本轮'),
        ),
      ),
    );
  }

  Widget _recoveryCard(InterviewService service) {
    final recovery = service.recoveryAttempt!;
    return Card(
      key: const Key('turn-recovery-card'),
      color: const Color(0xFFFFF7ED),
      margin: const EdgeInsets.only(top: 8, bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '本轮需要恢复：${recovery.status}',
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
            if (recovery.errorCode != null) Text('错误代码：${recovery.errorCode}'),
            const SizedBox(height: 10),
            TextField(
              controller: _recoveryController,
              minLines: 2,
              maxLines: 4,
              decoration: const InputDecoration(border: OutlineInputBorder()),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              children: [
                FilledButton(
                  onPressed: () =>
                      service.retryRecovery(_recoveryController.text),
                  child: const Text('重试本轮'),
                ),
                OutlinedButton(
                  onPressed: service.discardRecovery,
                  child: const Text('丢弃本轮'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _conflictCard(InterviewService service) {
    return Card(
      color: const Color(0xFFFFF7ED),
      child: ListTile(
        leading: const Icon(Icons.sync_problem, color: Color(0xFFD97706)),
        title: Text(service.conflictMessage!),
        trailing: TextButton(
          onPressed: service.refreshReplay,
          child: const Text('刷新'),
        ),
      ),
    );
  }

  Widget _inlineError(InterviewService service) {
    return Card(
      color: const Color(0xFFFEF2F2),
      child: ListTile(
        leading: const Icon(Icons.error_outline, color: Color(0xFFDC2626)),
        title: Text(service.replayError!),
      ),
    );
  }

  Widget _tag(String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(text, style: TextStyle(fontSize: 10, color: color)),
    );
  }

  Widget _statusBadge(int status) {
    final (label, color) = switch (status) {
      1 => ('进行中', const Color(0xFF2563EB)),
      2 => ('已完成', const Color(0xFF16A34A)),
      _ => ('已结束', const Color(0xFF64748B)),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Text(label, style: TextStyle(fontSize: 11, color: color)),
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

  String _compactDateTime(DateTime value) {
    String two(int number) => number.toString().padLeft(2, '0');
    return '${value.year}-${two(value.month)}-${two(value.day)} '
        '${two(value.hour)}:${two(value.minute)}';
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
      isScrollControlled: true,
      builder: (context) => SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.75,
        child: _buildTree(service, inSheet: true),
      ),
    );
  }
}
