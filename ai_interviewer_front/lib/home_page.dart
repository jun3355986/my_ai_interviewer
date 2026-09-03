import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'design/app_design.dart';
import 'models/interview_history.dart';
import 'models/practice_stats.dart';
import 'models/user.dart';
import 'services/auth_service.dart';
import 'services/interview_service.dart';
import 'services/notification_service.dart';

/// 首页工作台：开始新面试、最近活动、个人练习指标与趋势，全部来自真实后端。
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  User? _currentUser;
  List<InterviewLineageSummary> _recentLineages = const [];
  PracticeStats? _practiceStats;
  EvaluationStatistics? _evaluationStats;
  bool _isLoading = true;
  String? _resumingBranchId;

  @override
  void initState() {
    super.initState();
    // 延后到帧结束再触发服务加载，避免在 build 阶段同步 notifyListeners。
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _loadData();
    });
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    final authService = context.read<AuthService>();
    final interviewService = context.read<InterviewService>();
    final notificationService = context.read<NotificationService>();

    final results = await Future.wait([
      authService.getMe(),
      _guard(
        () => interviewService.getHistory(current: 1, size: 5),
      ),
      _guard(interviewService.getMyStats),
      _guard(interviewService.getEvaluationStatistics),
      notificationService.loadAll(),
    ]);

    if (!mounted) return;
    setState(() {
      _currentUser = results[0] as User?;
      final historyPage = results[1] as dynamic;
      _recentLineages = historyPage?.records ?? const <InterviewLineageSummary>[];
      _practiceStats = results[2] as PracticeStats?;
      _evaluationStats = results[3] as EvaluationStatistics?;
      _isLoading = false;
    });
  }

  /// 单独兜底：一个统计接口失败不影响首页其它模块。
  Future<Object?> _guard<T>(Future<T> Function() loader) async {
    try {
      return await loader();
    } catch (_) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: Column(
          children: [
            _buildTopBar(),
            Expanded(
              child: RefreshIndicator(
                onRefresh: _loadData,
                child: _isLoading
                    ? const Center(child: CircularProgressIndicator())
                    : SingleChildScrollView(
                        physics: const AlwaysScrollableScrollPhysics(),
                        padding: const EdgeInsets.fromLTRB(
                          20,
                          22,
                          20,
                          32,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            PageHeader(
                              eyebrow: '练习工作区',
                              title: '欢迎回来，${_displayName()}',
                              description: '从一次面试练习开始，也可以查看自己的评分趋势、最近活动和历史分支。',
                            ),
                            const SizedBox(height: 20),
                            _buildWelcomeGrid(),
                            const SizedBox(height: 16),
                            _buildMetricGrid(),
                            const SizedBox(height: 16),
                            _buildDashboardCharts(),
                          ],
                        ),
                      ),
              ),
            ),
            AppBottomNav(
              current: 'home',
              onSelect: _onNavSelect,
            ),
          ],
        ),
      ),
    );
  }

  String _displayName() {
    final user = _currentUser;
    return user?.nickname?.trim().isNotEmpty == true
        ? user!.nickname!
        : user?.username.trim().isNotEmpty == true
        ? user!.username
        : '练习者';
  }

  Widget _buildTopBar() {
    final notificationService = context.watch<NotificationService>();
    final unread = notificationService.unreadCount;
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.bg,
        border: Border(bottom: BorderSide(color: AppColors.borderSoft)),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Row(
        children: [
          UserAvatar(
            nickname: _currentUser?.nickname,
            username: _currentUser?.username,
            avatarUrl: _currentUser?.avatarUrl,
          ),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '面试助手',
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                  color: AppColors.fg,
                ),
              ),
              const SizedBox(height: 1),
              Row(
                children: [
                  Container(
                    width: 7,
                    height: 7,
                    decoration: const BoxDecoration(
                      color: AppColors.success,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 5),
                  Text(
                    '服务正常',
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.muted,
                    ),
                  ),
                ],
              ),
            ],
          ),
          const Spacer(),
          Stack(
            clipBehavior: Clip.none,
            children: [
              IconButton(
                tooltip: '查看通知',
                onPressed: _openNotificationSheet,
                icon: const Icon(
                  Icons.notifications_none_rounded,
                  size: 24,
                  color: AppColors.fg2,
                ),
              ),
              if (unread > 0)
                Positioned(
                  top: 6,
                  right: 6,
                  child: Container(
                    padding: const EdgeInsets.all(4),
                    decoration: const BoxDecoration(
                      color: AppColors.danger,
                      shape: BoxShape.circle,
                    ),
                    child: Text(
                      unread > 9 ? '9+' : '$unread',
                      style: const TextStyle(
                        fontSize: 9,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ),
            ],
          ),
          IconButton(
            tooltip: '开始新面试',
            onPressed: () => Navigator.pushNamed(context, '/upload'),
            icon: const Icon(Icons.add_circle_outline, size: 24, color: AppColors.fg2),
          ),
        ],
      ),
    );
  }

  Widget _buildWelcomeGrid() {
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 760;
        final startCard = _buildStartCard();
        final activityCard = _buildActivityCard();
        if (wide) {
          return IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(flex: 13, child: startCard),
                const SizedBox(width: 16),
                Expanded(flex: 7, child: activityCard),
              ],
            ),
          );
        }
        return Column(
          children: [
            startCard,
            const SizedBox(height: 16),
            activityCard,
          ],
        );
      },
    );
  }

  Widget _buildStartCard() {
    return Container(
      padding: const EdgeInsets.all(26),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(AppRadius.lg),
        border: Border.all(color: AppColors.accent.withValues(alpha: 0.18)),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            AppColors.accent.withValues(alpha: 0.07),
            AppColors.accent.withValues(alpha: 0.02),
          ],
        ),
        boxShadow: AppShadows.card,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '下一步',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              letterSpacing: 1,
              color: AppColors.meta,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            '开始新面试',
            style: TextStyle(
              fontSize: 30,
              fontWeight: FontWeight.w700,
              letterSpacing: -0.5,
              color: AppColors.fg,
            ),
          ),
          const SizedBox(height: 10),
          const Text(
            '上传 PDF 简历，结合岗位要求生成有针对性的项目题、技术题和追问。',
            style: TextStyle(
              fontSize: 14,
              color: AppColors.muted,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 20),
          Align(
            alignment: Alignment.centerLeft,
            child: AppButton(
              label: '上传简历并开始',
              trailingIcon: Icons.arrow_forward,
              onPressed: () => Navigator.pushNamed(context, '/upload'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActivityCard() {
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                '最近活动',
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  color: AppColors.fg,
                ),
              ),
              TextButton(
                onPressed: () => Navigator.pushNamed(context, '/history'),
                style: TextButton.styleFrom(
                  foregroundColor: AppColors.fg2,
                  minimumSize: const Size(0, 32),
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                ),
                child: const Text(
                  '查看全部',
                  style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                ),
              ),
            ],
          ),
          if (_recentLineages.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 20),
              child: Center(
                child: Text(
                  '暂无面试记录',
                  style: const TextStyle(fontSize: 13, color: AppColors.muted),
                ),
              ),
            )
          else
            ..._recentLineages.map(_buildActivityRow),
        ],
      ),
    );
  }

  Widget _buildActivityRow(InterviewLineageSummary lineage) {
    final active = lineage.hasActiveBranch;
    final completed = lineage.completedBranchCount > 0;
    return Padding(
      padding: const EdgeInsets.only(top: 12, bottom: 4),
      child: Row(
        children: [
          Container(
            width: 34,
            height: 34,
            decoration: BoxDecoration(
              color: AppColors.accentSoft,
              borderRadius: BorderRadius.circular(AppRadius.md),
            ),
            child: Icon(
              completed ? Icons.insights : Icons.chat_bubble_outline,
              size: 18,
              color: AppColors.accent,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  lineage.displayTitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.fg,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  '${lineage.focusedBranchStageDisplay ?? '未知阶段'} · '
                  '${lineage.latestActivityAt.relativeDisplay}',
                  style: const TextStyle(fontSize: 12, color: AppColors.muted),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          if (active)
            AppButton(
              label: _resumingBranchId == lineage.focusedBranchId ? '恢复中…' : '继续',
              small: true,
              onPressed: _resumingBranchId == lineage.focusedBranchId
                  ? null
                  : () => _resumeLineage(lineage),
            )
          else if (completed)
            AppButton(
              label: '查看',
              small: true,
              variant: AppButtonVariant.ghost,
              onPressed: () =>
                  Navigator.pushNamed(context, '/history-detail', arguments: lineage),
            )
          else
            const AppBadge('已结束', tone: AppBadgeTone.neutral),
        ],
      ),
    );
  }

  Future<void> _resumeLineage(InterviewLineageSummary lineage) async {
    setState(() => _resumingBranchId = lineage.focusedBranchId);
    try {
      await context.read<InterviewService>().resumeInterview(
        lineage.focusedBranchId,
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

  Widget _buildMetricGrid() {
    final stats = _practiceStats;
    final evalStats = _evaluationStats;
    final metrics = [
      _MetricData(
        label: '总面试次数',
        value: stats == null ? '—' : '${stats.totalLineages}',
        hint: stats == null ? '统计暂不可用' : '按面试谱系统计',
      ),
      _MetricData(
        label: '平均得分',
        value: evalStats == null || evalStats.averageScore == 0
            ? '—'
            : '${evalStats.averageScore}',
        hint: evalStats == null ? '统计暂不可用' : '已完成评估的平均分',
      ),
      _MetricData(
        label: '最近面试',
        value: stats?.latestActivityAt.monthDayDisplay ?? '--',
        smallValue: true,
        hint: stats == null ? '统计暂不可用' : '最近一次练习活动',
      ),
    ];
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 760;
        return IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var i = 0; i < metrics.length; i++) ...[
                Expanded(child: _buildMetricCard(metrics[i], compact: !wide)),
                if (i < metrics.length - 1) SizedBox(width: wide ? 12 : 10),
              ],
            ],
          ),
        );
      },
    );
  }

  Widget _buildMetricCard(_MetricData metric, {required bool compact}) {
    return AppCard(
      padding: EdgeInsets.all(compact ? 14 : 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            metric.label,
            style: TextStyle(
              fontSize: compact ? 12 : 13,
              color: AppColors.muted,
            ),
          ),
          SizedBox(height: compact ? 6 : 10),
          Text(
            metric.value,
            style: TextStyle(
              fontSize: compact
                  ? (metric.smallValue ? 20 : 26)
                  : (metric.smallValue ? 26 : 34),
              fontWeight: FontWeight.w700,
              letterSpacing: compact ? -0.5 : -1,
              color: AppColors.fg,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
          SizedBox(height: compact ? 4 : 8),
          Text(
            metric.hint,
            style: TextStyle(
              fontSize: compact ? 11 : 12,
              height: 1.3,
              color: AppColors.meta,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDashboardCharts() {
    final trendCard = _buildTrendCard();
    final distributionCard = _buildDistributionCard();
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 760;
        if (wide) {
          return IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(flex: 13, child: trendCard),
                const SizedBox(width: 16),
                Expanded(flex: 7, child: distributionCard),
              ],
            ),
          );
        }
        return Column(
          children: [
            trendCard,
            const SizedBox(height: 16),
            distributionCard,
          ],
        );
      },
    );
  }

  Widget _buildTrendCard() {
    final stats = _practiceStats;
    final trend = stats?.dailyTrend ?? const <TrendPoint>[];
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '近 14 天面试趋势',
            style: TextStyle(
              fontSize: 17,
              fontWeight: FontWeight.w700,
              color: AppColors.fg,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            '按面试谱系创建时间统计的个人练习',
            style: TextStyle(fontSize: 12, color: AppColors.muted),
          ),
          const SizedBox(height: 18),
          if (stats == null)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 28),
              child: Center(
                child: Text('趋势数据暂不可用', style: TextStyle(color: AppColors.muted)),
              ),
            )
          else
            SizedBox(
              height: 170,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  for (var i = 0; i < trend.length; i++) ...[
                    if (i > 0) const SizedBox(width: 6),
                    Expanded(child: _TrendBar(point: trend[i], maxCount: _maxCount(trend))),
                  ],
                ],
              ),
            ),
        ],
      ),
    );
  }

  int _maxCount(List<TrendPoint> trend) {
    var max = 0;
    for (final point in trend) {
      if (point.count > max) max = point.count;
    }
    return max;
  }

  Widget _buildDistributionCard() {
    final evalStats = _evaluationStats;
    final distribution = evalStats?.scoreDistribution ?? const <ScoreBucket>[];
    final maxCount = distribution.fold<int>(
      0,
      (current, bucket) => bucket.count > current ? bucket.count : current,
    );
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '分数分布',
            style: TextStyle(
              fontSize: 17,
              fontWeight: FontWeight.w700,
              color: AppColors.fg,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            '个人练习评估报告',
            style: TextStyle(fontSize: 12, color: AppColors.muted),
          ),
          const SizedBox(height: 18),
          if (evalStats == null)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 28),
              child: Center(
                child: Text('分布数据暂不可用', style: TextStyle(color: AppColors.muted)),
              ),
            )
          else
            Column(
              children: [
                for (var i = 0; i < distribution.length; i++) ...[
                  if (i > 0) const SizedBox(height: 14),
                  _DistributionRow(
                    bucket: distribution[i],
                    color: _distributionColor(i),
                    maxCount: maxCount,
                  ),
                ],
              ],
            ),
        ],
      ),
    );
  }

  Color _distributionColor(int index) {
    return switch (index) {
      0 => AppColors.success,
      1 => AppColors.accent,
      2 => AppColors.accentHover,
      3 => AppColors.warn,
      _ => AppColors.danger,
    };
  }

  void _openNotificationSheet() {
    final service = context.read<NotificationService>();
    service.loadAll();
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: AppColors.bg,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.lg)),
      ),
      isScrollControlled: true,
      builder: (sheetContext) => DraggableScrollableSheet(
        initialChildSize: 0.6,
        maxChildSize: 0.85,
        minChildSize: 0.35,
        expand: false,
        builder: (sheetContext, scrollController) => Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 16, 8),
              child: Row(
                children: [
                  const Text(
                    '通知',
                    style: TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                      color: AppColors.fg,
                    ),
                  ),
                  const Spacer(),
                  TextButton(
                    onPressed: () async {
                      try {
                        await service.markAllAsRead();
                      } catch (_) {}
                    },
                    child: const Text(
                      '全部已读',
                      style: TextStyle(fontSize: 13, color: AppColors.accent),
                    ),
                  ),
                ],
              ),
            ),
            const Divider(height: 1, color: AppColors.borderSoft),
            Expanded(
              child: _NotificationList(service: service),
            ),
          ],
        ),
      ),
    );
  }

  void _onNavSelect(String id) {
    switch (id) {
      case 'home':
        break;
      case 'interview':
        Navigator.pushNamedAndRemoveUntil(
          context,
          '/upload',
          ModalRoute.withName('/home'),
        );
      case 'history':
        Navigator.pushNamedAndRemoveUntil(
          context,
          '/history',
          ModalRoute.withName('/home'),
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

class _MetricData {
  const _MetricData({
    required this.label,
    required this.value,
    required this.hint,
    this.smallValue = false,
  });

  final String label;
  final String value;
  final String hint;
  final bool smallValue;
}

class _TrendBar extends StatelessWidget {
  const _TrendBar({required this.point, required this.maxCount});

  final TrendPoint point;
  final int maxCount;

  @override
  Widget build(BuildContext context) {
    final ratio = maxCount <= 0
        ? 0.0
        : (point.count / maxCount).clamp(0.08, 1.0);
    return Column(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        if (point.count > 0)
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: Text(
              '${point.count}',
              style: const TextStyle(
                fontSize: 10,
                color: AppColors.muted,
              ),
            ),
          ),
        Container(
          height: 110 * ratio,
          decoration: BoxDecoration(
            color: AppColors.accent.withValues(alpha: point.count > 0 ? 0.85 : 0.2),
            borderRadius: const BorderRadius.vertical(
              top: Radius.circular(4),
            ),
          ),
        ),
        const SizedBox(height: 6),
        Text(
          point.shortLabel.length >= 3 ? point.shortLabel.substring(3) : '',
          style: const TextStyle(fontSize: 9, color: AppColors.meta),
        ),
      ],
    );
  }
}

class _DistributionRow extends StatelessWidget {
  const _DistributionRow({
    required this.bucket,
    required this.color,
    required this.maxCount,
  });

  final ScoreBucket bucket;
  final Color color;
  final int maxCount;

  @override
  Widget build(BuildContext context) {
    final ratio = maxCount <= 0 ? 0.0 : (bucket.count / maxCount).clamp(0.0, 1.0);
    return Column(
      children: [
        Row(
          children: [
            SizedBox(
              width: 56,
              child: Text(
                bucket.range,
                style: const TextStyle(fontSize: 12, color: AppColors.fg2),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(child: AppMeter(value: ratio, color: color)),
            const SizedBox(width: 10),
            SizedBox(
              width: 28,
              child: Text(
                bucket.count > 0 ? '${bucket.count}' : '—',
                textAlign: TextAlign.right,
                style: const TextStyle(fontSize: 12, color: AppColors.muted),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _NotificationList extends StatelessWidget {
  const _NotificationList({required this.service});

  final NotificationService service;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: service,
      builder: (context, _) {
        if (service.isLoading) {
          return const Center(child: CircularProgressIndicator());
        }
        if (service.error != null && service.items.isEmpty) {
          return EmptyState(
            icon: Icons.cloud_off_outlined,
            title: '通知加载失败',
            message: service.error!,
            actionLabel: '重新加载',
            onAction: service.loadAll,
          );
        }
        if (service.items.isEmpty) {
          return const EmptyState(
            icon: Icons.notifications_none,
            title: '暂无通知',
            message: '评估报告生成等站内消息会出现在这里。',
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
          itemCount: service.items.length,
          separatorBuilder: (_, _) => const Divider(
            height: 1,
            color: AppColors.borderSoft,
          ),
          itemBuilder: (context, index) {
            final item = service.items[index];
            return ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 4),
              title: Row(
                children: [
                  Expanded(
                    child: Text(
                      item.title,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: item.isRead
                            ? FontWeight.w500
                            : FontWeight.w700,
                        color: AppColors.fg,
                      ),
                    ),
                  ),
                  if (!item.isRead)
                    Container(
                      width: 8,
                      height: 8,
                      decoration: const BoxDecoration(
                        color: AppColors.danger,
                        shape: BoxShape.circle,
                      ),
                    ),
                ],
              ),
              subtitle: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (item.content.isNotEmpty)
                    Text(
                      item.content,
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.muted,
                      ),
                    ),
                  const SizedBox(height: 3),
                  Text(
                    '${item.typeText} · ${item.createdAt.relativeDisplay}',
                    style: const TextStyle(fontSize: 11, color: AppColors.meta),
                  ),
                ],
              ),
              onTap: item.isRead ? null : () => service.markAsRead(item.id),
            );
          },
        );
      },
    );
  }
}
