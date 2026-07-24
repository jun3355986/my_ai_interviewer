import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'models/interview_history.dart';
import 'services/interview_service.dart';

class InterviewHistoryPage extends StatefulWidget {
  const InterviewHistoryPage({super.key});

  @override
  State<InterviewHistoryPage> createState() => _InterviewHistoryPageState();
}

class _InterviewHistoryPageState extends State<InterviewHistoryPage> {
  final TextEditingController _searchController = TextEditingController();

  String _sortBy = 'time';
  String _status = 'all';
  bool _loading = true;
  String? _error;
  InterviewLineagePage? _page;
  String? _resumingBranchId;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadHistory());
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadHistory({int current = 1}) async {
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = await context.read<InterviewService>().getHistory(
        current: current,
        size: 10,
        keyword: _searchController.text,
        sortBy: _sortBy,
        status: _status,
      );
      if (!mounted) return;
      setState(() {
        _page = page;
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF9FAFB),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),
            Expanded(child: _buildBody()),
            _buildBottomNavigationBar(),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 20, 24, 16),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(
          bottom: BorderSide(color: Color(0xFFE5E7EB), width: 0.5),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '面试历史',
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: Color(0xFF1E2939),
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _searchController,
            textInputAction: TextInputAction.search,
            onSubmitted: (_) => _loadHistory(),
            decoration: InputDecoration(
              hintText: '搜索岗位或候选人',
              prefixIcon: const Icon(Icons.search, size: 20),
              suffixIcon: IconButton(
                tooltip: '搜索',
                onPressed: _loadHistory,
                icon: const Icon(Icons.arrow_forward, size: 20),
              ),
              filled: true,
              fillColor: const Color(0xFFF3F4F6),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              _buildSortButton('按时间', 'time'),
              const SizedBox(width: 8),
              _buildSortButton('按最佳评分', 'score'),
            ],
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _buildStatusButton('全部', 'all'),
              _buildStatusButton('进行中', 'active'),
              _buildStatusButton('已完成', 'completed'),
              _buildStatusButton('已结束', 'ended'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSortButton(String label, String value) {
    final selected = _sortBy == value;
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      showCheckmark: false,
      selectedColor: const Color(0xFF2B7FFF),
      backgroundColor: const Color(0xFFF3F4F6),
      labelStyle: TextStyle(
        color: selected ? Colors.white : const Color(0xFF4A5565),
        fontWeight: FontWeight.w500,
      ),
      side: BorderSide.none,
      onSelected: (_) {
        if (_sortBy == value) return;
        setState(() => _sortBy = value);
        _loadHistory();
      },
    );
  }

  Widget _buildStatusButton(String label, String value) {
    final selected = _status == value;
    return ChoiceChip(
      key: Key('history-status-$value'),
      label: Text(label),
      selected: selected,
      showCheckmark: false,
      selectedColor: const Color(0xFF0F766E),
      backgroundColor: const Color(0xFFF3F4F6),
      labelStyle: TextStyle(
        color: selected ? Colors.white : const Color(0xFF4A5565),
        fontWeight: FontWeight.w500,
      ),
      side: BorderSide.none,
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
      return _buildState(
        icon: Icons.cloud_off_outlined,
        title: '历史加载失败',
        message: _error!,
        actionLabel: '重新加载',
        onAction: _loadHistory,
      );
    }

    final page = _page;
    if (page == null || page.records.isEmpty) {
      return _buildState(
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
      onRefresh: () => _loadHistory(current: page.current),
      child: ListView.separated(
        padding: const EdgeInsets.all(24),
        itemCount: page.records.length + (page.pages > 1 ? 1 : 0),
        separatorBuilder: (_, _) => const SizedBox(height: 12),
        itemBuilder: (context, index) {
          if (index == page.records.length) {
            return _buildPagination(page);
          }
          return _buildRecordCard(page.records[index]);
        },
      ),
    );
  }

  Widget _buildState({
    required IconData icon,
    required String title,
    required String message,
    required String actionLabel,
    required VoidCallback onAction,
  }) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 52, color: const Color(0xFF99A1AF)),
            const SizedBox(height: 16),
            Text(
              title,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: Color(0xFF1E2939),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Color(0xFF6A7282), height: 1.5),
            ),
            const SizedBox(height: 20),
            FilledButton(onPressed: onAction, child: Text(actionLabel)),
          ],
        ),
      ),
    );
  }

  Widget _buildRecordCard(InterviewLineageSummary record) {
    final active = record.hasActiveBranch;
    final statusColor = active
        ? const Color(0xFF2B7FFF)
        : record.completedBranchCount > 0
        ? const Color(0xFF00A63E)
        : const Color(0xFF6A7282);
    final statusText = active
        ? '进行中'
        : record.completedBranchCount > 0
        ? '已完成'
        : '已结束';

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE5E7EB)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  record.displayTitle,
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF1E2939),
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  statusText,
                  style: TextStyle(
                    color: statusColor,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (record.candidateName?.trim().isNotEmpty == true)
            Text(
              record.candidateName!,
              style: const TextStyle(fontSize: 14, color: Color(0xFF4A5565)),
            ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 16,
            runSpacing: 8,
            children: [
              _metadata(
                Icons.schedule,
                _formatDateTime(record.latestActivityAt),
              ),
              _metadata(
                Icons.account_tree_outlined,
                '${record.branchCount} 个分支',
              ),
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
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: record.focusedBranchProgress.clamp(0, 100) / 100,
                    minHeight: 7,
                    backgroundColor: const Color(0xFFE5E7EB),
                    color: statusColor,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Text(
                record.bestCompletedScore == null
                    ? '${record.focusedBranchProgress}%'
                    : '最佳 ${record.bestCompletedScore} 分',
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF4A5565),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _openReplay(record),
                  icon: const Icon(Icons.replay_outlined, size: 18),
                  label: const Text('面试回放'),
                ),
              ),
              if (active) ...[
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _resumingBranchId == record.focusedBranchId
                        ? null
                        : () => _continueInterview(record),
                    icon: _resumingBranchId == record.focusedBranchId
                        ? const SizedBox.square(
                            dimension: 16,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Icon(Icons.play_arrow_rounded, size: 18),
                    label: const Text('继续面试'),
                  ),
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
        Icon(icon, size: 15, color: const Color(0xFF99A1AF)),
        const SizedBox(width: 5),
        Text(
          text,
          style: const TextStyle(fontSize: 13, color: Color(0xFF6A7282)),
        ),
      ],
    );
  }

  Widget _buildPagination(InterviewLineagePage page) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        IconButton(
          tooltip: '上一页',
          onPressed: page.current > 1
              ? () => _loadHistory(current: page.current - 1)
              : null,
          icon: const Icon(Icons.chevron_left),
        ),
        Text('${page.current} / ${page.pages}'),
        IconButton(
          tooltip: '下一页',
          onPressed: page.current < page.pages
              ? () => _loadHistory(current: page.current + 1)
              : null,
          icon: const Icon(Icons.chevron_right),
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
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('恢复面试失败：${_friendlyError(error)}')),
      );
    } finally {
      if (mounted) {
        setState(() => _resumingBranchId = null);
      }
    }
  }

  Widget _buildBottomNavigationBar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: Color(0xFFE5E7EB), width: 0.5)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _navItem(
            icon: Icons.home_outlined,
            label: '首页',
            onTap: () => Navigator.pushReplacementNamed(context, '/home'),
          ),
          _navItem(
            icon: Icons.chat_bubble_outline,
            label: '面试',
            onTap: () => Navigator.pushNamed(context, '/upload'),
          ),
          _navItem(icon: Icons.history, label: '历史', active: true),
          _navItem(icon: Icons.settings_outlined, label: '设置'),
        ],
      ),
    );
  }

  Widget _navItem({
    required IconData icon,
    required String label,
    bool active = false,
    VoidCallback? onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 24,
              color: active ? const Color(0xFF155DFC) : const Color(0xFF99A1AF),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                fontSize: 12,
                color: active
                    ? const Color(0xFF155DFC)
                    : const Color(0xFF99A1AF),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _formatDateTime(DateTime? value) {
    if (value == null) return '暂无时间';
    return '${value.year}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')} '
        '${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';
  }

  String _friendlyError(Object error) {
    final text = error.toString();
    return text.startsWith('Bad state: ') ? text.substring(11) : text;
  }
}
