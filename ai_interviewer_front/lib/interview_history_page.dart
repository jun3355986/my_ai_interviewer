import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'design/app_design.dart';
import 'models/interview_history.dart';
import 'services/interview_service.dart';

/// 面试历史：搜索 + 排序/状态筛选 + 滚动加载更多，全部来自真实谱系数据。
class InterviewHistoryPage extends StatefulWidget {
  const InterviewHistoryPage({super.key});

  @override
  State<InterviewHistoryPage> createState() => _InterviewHistoryPageState();
}

class _InterviewHistoryPageState extends State<InterviewHistoryPage> {
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  static const int _pageSize = 10;

  String _sortBy = 'time';
  String _status = 'all';
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;
  List<InterviewLineageSummary> _records = const [];
  int _currentPage = 1;
  int _totalPages = 1;
  bool _hasMore = true;
  String? _resumingBranchId;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadHistory());
  }

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!_scrollController.hasClients || _loading || _loadingMore) return;
    final position = _scrollController.position;
    if (position.pixels >= position.maxScrollExtent - 240) {
      _loadMore();
    }
  }

  Future<void> _loadHistory() async {
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = await context.read<InterviewService>().getHistory(
        current: 1,
        size: _pageSize,
        keyword: _searchController.text,
        sortBy: _sortBy,
        status: _status,
      );
      if (!mounted) return;
      setState(() {
        _records = page.records;
        _currentPage = page.current;
        _totalPages = page.pages;
        _hasMore = page.current < page.pages && page.records.isNotEmpty;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = _friendlyError(error);
      });
    }
  }

  Future<void> _loadMore() async {
    if (!_hasMore || _loading || _loadingMore) return;
    setState(() => _loadingMore = true);
    try {
      final page = await context.read<InterviewService>().getHistory(
        current: _currentPage + 1,
        size: _pageSize,
        keyword: _searchController.text,
        sortBy: _sortBy,
        status: _status,
      );
      if (!mounted) return;
      setState(() {
        _records = [..._records, ...page.records];
        _currentPage = page.current;
        _totalPages = page.pages;
        _hasMore = page.current < page.pages && page.records.isNotEmpty;
        _loadingMore = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loadingMore = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),
            Expanded(child: _buildBody()),
            AppBottomNav(
              current: 'history',
              onSelect: _onNavSelect,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      width: double.infinity,
      decoration: const BoxDecoration(
        color: AppColors.bg,
        border: Border(bottom: BorderSide(color: AppColors.borderSoft)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Expanded(
                child: Text(
                  '面试历史',
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.5,
                    color: AppColors.fg,
                  ),
                ),
              ),
              AppButton(
                label: '开始面试',
                variant: AppButtonVariant.secondary,
                small: true,
                onPressed: () => Navigator.pushNamed(
                  context,
                  '/upload',
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          const Text(
            '一个面试谱系只占一张卡片，集中呈现进度、最佳评分、分支数量、回放与继续入口。',
            style: TextStyle(fontSize: 12, color: AppColors.muted, height: 1.4),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _searchController,
                  textInputAction: TextInputAction.search,
                  onSubmitted: (_) => _loadHistory(),
                  decoration: appInputDecoration(
                    hint: '搜索岗位或候选人',
                    prefix: const Icon(Icons.search, size: 19, color: AppColors.meta),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              AppButton(
                label: '搜索',
                variant: AppButtonVariant.secondary,
                onPressed: _loadHistory,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              AppFilterChip(
                label: '按时间',
                selected: _sortBy == 'time',
                onSelected: (_) {
                  if (_sortBy == 'time') return;
                  setState(() => _sortBy = 'time');
                  _loadHistory();
                },
              ),
              AppFilterChip(
                label: '按最佳评分',
                selected: _sortBy == 'score',
                onSelected: (_) {
                  if (_sortBy == 'score') return;
                  setState(() => _sortBy = 'score');
                  _loadHistory();
                },
              ),
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _buildStatusChip('全部', 'all'),
              _buildStatusChip('进行中', 'active'),
              _buildStatusChip('已完成', 'completed'),
              _buildStatusChip('已结束', 'ended'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildStatusChip(String label, String value) {
    return AppFilterChip(
      keyOverride: Key('history-status-$value'),
      label: label,
      selected: _status == value,
      onSelected: (_) {
        if (_status == value) return;
        setState(() => _status = value);
        _loadHistory();
      },
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return EmptyState(
        icon: Icons.cloud_off_outlined,
        title: '历史加载失败',
        message: _error!,
        actionLabel: '重新加载',
        onAction: _loadHistory,
      );
    }

    if (_records.isEmpty) {
      return EmptyState(
        icon: Icons.history_toggle_off,
        title: '暂无面试记录',
        message: _searchController.text.trim().isEmpty
            ? '完成或中断一次面试后，会在这里保留真实进度。'
            : '没有找到匹配的面试记录。',
        actionLabel: _searchController.text.trim().isEmpty ? '开始面试' : '清除搜索',
        onAction: () {
          if (_searchController.text.trim().isEmpty) {
            Navigator.pushNamed(context, '/upload');
          } else {
            _searchController.clear();
            _loadHistory();
          }
        },
      );
    }

    return RefreshIndicator(
      onRefresh: _loadHistory,
      child: ListView.separated(
        controller: _scrollController,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(20),
        itemCount: _records.length + 1,
        separatorBuilder: (_, _) => const SizedBox(height: 12),
        itemBuilder: (context, index) {
          if (index == _records.length) {
            return _buildFeedStatus();
          }
          return _buildRecordCard(_records[index]);
        },
      ),
    );
  }

  Widget _buildFeedStatus() {
    if (_loadingMore) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 18),
        child: Center(
          child: SizedBox.square(
            dimension: 18,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 18),
      child: Center(
        child: _hasMore
            ? GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: _loadMore,
                child: const Padding(
                  padding: EdgeInsets.symmetric(vertical: 10, horizontal: 16),
                  child: Text(
                    '向下滚动加载更多',
                    style: TextStyle(fontSize: 12, color: AppColors.meta),
                  ),
                ),
              )
            : Text(
                '已显示全部 $_currentPage/$_totalPages 页记录',
                style: const TextStyle(fontSize: 12, color: AppColors.meta),
              ),
      ),
    );
  }

  Widget _buildRecordCard(InterviewLineageSummary record) {
    final active = record.hasActiveBranch;
    final (_, tone, statusText) = lineageStatusView(
      active,
      record.completedBranchCount,
    );
    final statusColor = switch (tone) {
      AppBadgeTone.accent => AppColors.accent,
      AppBadgeTone.success => AppColors.success,
      _ => AppColors.muted,
    };

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      record.displayTitle,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.fg,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      record.candidateName?.trim().isNotEmpty == true
                          ? '${record.candidateName} · ${record.branchCount} 个分支'
                          : '${record.branchCount} 个分支 · 进行中 ${record.activeBranchCount}',
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.muted,
                      ),
                    ),
                  ],
                ),
              ),
              AppBadge(statusText, tone: tone),
            ],
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 16,
            runSpacing: 6,
            children: [
              _metadata(Icons.schedule, record.latestActivityAt.fullDisplay),
              _metadata(
                Icons.flag_outlined,
                record.focusedBranchStageDisplay ?? '未知阶段',
              ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: AppMeter(
                  value: record.focusedBranchProgress.clamp(0, 100) / 100,
                  color: statusColor,
                ),
              ),
              const SizedBox(width: 12),
              Text(
                record.bestCompletedScore == null
                    ? '${record.focusedBranchProgress}%'
                    : '最佳 ${record.bestCompletedScore} 分',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: AppColors.muted,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              AppButton(
                label: '面试回放',
                variant: AppButtonVariant.secondary,
                small: true,
                icon: Icons.replay_outlined,
                onPressed: () => _openReplay(record),
              ),
              if (active) ...[
                const SizedBox(width: 8),
                AppButton(
                  label: _resumingBranchId == record.focusedBranchId
                      ? '恢复中…'
                      : '继续面试',
                  small: true,
                  loading: _resumingBranchId == record.focusedBranchId,
                  onPressed: _resumingBranchId == record.focusedBranchId
                      ? null
                      : () => _continueInterview(record),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  Widget _metadata(IconData icon, String text) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: AppColors.meta),
        const SizedBox(width: 5),
        Text(
          text,
          style: const TextStyle(fontSize: 12, color: AppColors.muted),
        ),
      ],
    );
  }

  void _openReplay(InterviewLineageSummary record) {
    Navigator.pushNamed(context, '/history-detail', arguments: record);
  }

  Future<void> _continueInterview(InterviewLineageSummary record) async {
    setState(() => _resumingBranchId = record.focusedBranchId);
    try {
      await context.read<InterviewService>().resumeInterview(
        record.focusedBranchId,
      );
      if (!mounted) return;
      Navigator.pushNamed(context, '/chat');
    } catch (error) {
      if (!mounted) return;
      showAppToast(context, '恢复面试失败：${_friendlyError(error)}');
    } finally {
      if (mounted) setState(() => _resumingBranchId = null);
    }
  }

  void _onNavSelect(String id) {
    switch (id) {
      case 'history':
        break;
      case 'interview':
        Navigator.pushNamedAndRemoveUntil(
          context,
          '/upload',
          ModalRoute.withName('/home'),
        );
      case 'home':
        Navigator.pushNamedAndRemoveUntil(
          context,
          '/home',
          (route) => false,
        );
      case 'settings':
        Navigator.pushNamedAndRemoveUntil(
          context,
          '/settings',
          ModalRoute.withName('/home'),
        );
    }
  }

  String _friendlyError(Object error) {
    final text = error.toString();
    return text.startsWith('Bad state: ') ? text.substring(11) : text;
  }
}
