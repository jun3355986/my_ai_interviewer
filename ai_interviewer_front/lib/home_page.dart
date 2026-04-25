import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'services/auth_service.dart';
import 'services/job_service.dart';
import 'models/job.dart';
import 'models/user.dart';

/// AI 面试官助手 - 主页
/// 基于 Figma 设计实现
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  User? _currentUser;
  List<Job> _jobs = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final authService = Provider.of<AuthService>(context, listen: false);
    final jobService = Provider.of<JobService>(context, listen: false);

    setState(() => _isLoading = true);

    final user = await authService.getMe();
    final jobs = await jobService.getJobs();

    if (mounted) {
      setState(() {
        _currentUser = user;
        _jobs = jobs;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F7),
      body: SafeArea(
        child: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : Column(
                children: [
                  _buildTopBar(),
                  Expanded(
                    child: RefreshIndicator(
                      onRefresh: _loadData,
                      child: SingleChildScrollView(
                        physics: const AlwaysScrollableScrollPhysics(),
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _buildWelcomeSection(),
                            const SizedBox(height: 24),
                            _buildStartInterviewCard(context),
                            const SizedBox(height: 32),
                            const Text(
                              '数据概览',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                                color: Color(0xFF1A1A1A),
                              ),
                            ),
                            const SizedBox(height: 16),
                            _buildDataOverview(),
                            const SizedBox(height: 32),
                            _buildActivityHeader(context),
                            const SizedBox(height: 16),
                            _buildActivityList(),
                          ],
                        ),
                      ),
                    ),
                  ),
                  _buildBottomNavigationBar(context),
                ],
              ),
      ),
    );
  }

  Widget _buildTopBar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: const Color(0xFFE8E8E8),
              shape: BoxShape.circle,
              image: _currentUser?.avatarUrl != null
                  ? DecorationImage(
                      image: NetworkImage(_currentUser!.avatarUrl!),
                    )
                  : null,
              border: Border.all(color: const Color(0xFFDDDDDD), width: 2),
            ),
            child: _currentUser?.avatarUrl == null
                ? const Icon(Icons.person, color: Colors.grey)
                : null,
          ),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '你好',
                style: TextStyle(fontSize: 12, color: Color(0xFF999999)),
              ),
              Text(
                _currentUser?.nickname ?? _currentUser?.username ?? '加载中...',
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF1A1A1A),
                ),
              ),
            ],
          ),

          const Spacer(),

          // 通知图标
          Stack(
            clipBehavior: Clip.none,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: const Color(0xFFF5F5F7),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(
                  Icons.notifications_outlined,
                  color: Color(0xFF666666),
                  size: 22,
                ),
              ),
              // 红点提示
              Positioned(
                top: 8,
                right: 8,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: Color(0xFFFF3B30),
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// 欢迎区域
  Widget _buildWelcomeSection() {
    return const Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '欢迎回来！',
          style: TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.bold,
            color: Color(0xFF1A1A1A),
          ),
        ),
        SizedBox(height: 8),
        Text(
          '准备好开始新的面试练习了吗？',
          style: TextStyle(fontSize: 15, color: Color(0xFF666666)),
        ),
      ],
    );
  }

  /// 开始新面试大卡片
  Widget _buildStartInterviewCard(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF4E7FF6), Color(0xFF6B9DFF)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF4E7FF6).withValues(alpha: 0.3),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '开始新面试',
            style: TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            '上传简历，开始AI模拟面试',
            style: TextStyle(fontSize: 14, color: Colors.white70),
          ),
          const SizedBox(height: 20),

          // 立即开始按钮
          GestureDetector(
            onTap: () {
              Navigator.pushNamed(context, '/upload');
            },
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.1),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    '立即开始',
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF4E7FF6),
                    ),
                  ),
                  SizedBox(width: 8),
                  Icon(Icons.arrow_forward, color: Color(0xFF4E7FF6), size: 18),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// 数据概览
  Widget _buildDataOverview() {
    return Row(
      children: [
        // 总面试次数
        Expanded(
          child: _buildStatCard(
            icon: Icons.trending_up,
            iconColor: const Color(0xFF4E7FF6),
            iconBgColor: const Color(0xFFE8F0FE),
            value: '15',
            label: '总面试次数',
          ),
        ),
        const SizedBox(width: 12),

        // 平均得分
        Expanded(
          child: _buildStatCard(
            icon: Icons.check_circle,
            iconColor: const Color(0xFF34C759),
            iconBgColor: const Color(0xFFE8F8ED),
            value: '85',
            label: '平均得分',
          ),
        ),
        const SizedBox(width: 12),

        // 最近面试
        Expanded(
          child: _buildStatCard(
            icon: Icons.calendar_today,
            iconColor: const Color(0xFFAF52DE),
            iconBgColor: const Color(0xFFF3EAFA),
            value: '12-10',
            label: '最近面试',
          ),
        ),
      ],
    );
  }

  /// 统计卡片
  Widget _buildStatCard({
    required IconData icon,
    required Color iconColor,
    required Color iconBgColor,
    required String value,
    required String label,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        children: [
          // 图标
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: iconBgColor,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: iconColor, size: 24),
          ),
          const SizedBox(height: 12),

          // 数值
          Text(
            value,
            style: const TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 4),

          // 标签
          Text(
            label,
            style: const TextStyle(fontSize: 12, color: Color(0xFF999999)),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  /// 最近活动标题
  Widget _buildActivityHeader(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        const Text(
          '最近活动',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: Color(0xFF1A1A1A),
          ),
        ),
        TextButton(
          onPressed: () {
            Navigator.pushNamed(context, '/history');
          },
          child: const Text(
            '查看全部',
            style: TextStyle(fontSize: 14, color: Color(0xFF4E7FF6)),
          ),
        ),
      ],
    );
  }

  /// 最近活动列表
  Widget _buildActivityList() {
    if (_jobs.isEmpty) {
      return Container(
        height: 150,
        alignment: Alignment.center,
        child: const Text('暂无职位信息', style: TextStyle(color: Colors.grey)),
      );
    }
    return Column(
      children: _jobs
          .map(
            (job) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: _buildActivityItem(
                title: job.title,
                date: job.company ?? '未知公司',
                duration: job.location ?? '未知地点',
                score: 0, // 初始分数为0或根据业务逻辑显示
                job: job,
              ),
            ),
          )
          .toList(),
    );
  }

  /// 活动项
  Widget _buildActivityItem({
    required String title,
    required String date,
    required String duration,
    required int score,
    required Job job,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    const Icon(
                      Icons.calendar_today,
                      size: 14,
                      color: Color(0xFF999999),
                    ),
                    const SizedBox(width: 4),
                    Text(
                      date,
                      style: const TextStyle(
                        fontSize: 13,
                        color: Color(0xFF999999),
                      ),
                    ),
                    const SizedBox(width: 16),
                    const Icon(
                      Icons.access_time,
                      size: 14,
                      color: Color(0xFF999999),
                    ),
                    const SizedBox(width: 4),
                    Text(
                      duration,
                      style: const TextStyle(
                        fontSize: 13,
                        color: Color(0xFF999999),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),

          // 分数徽章
          Container(
            width: 52,
            height: 52,
            decoration: BoxDecoration(
              color: const Color(0xFF00C853),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Center(
              child: Text(
                score.toString(),
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// 底部导航栏
  Widget _buildBottomNavigationBar(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.8),
        border: const Border(
          top: BorderSide(color: Color(0x80E5E7EB), width: 0.5),
        ),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          // 首页
          _buildNavItem(
            icon: Icons.home,
            label: '首页',
            isActive: true,
            onTap: () {},
          ),

          // 面试
          _buildNavItem(
            icon: Icons.chat_bubble_outline,
            label: '面试',
            isActive: false,
            onTap: () {
              Navigator.pushNamed(context, '/upload');
            },
          ),

          // 历史
          _buildNavItem(
            icon: Icons.access_time,
            label: '历史',
            isActive: false,
            onTap: () {
              Navigator.pushNamed(context, '/history');
            },
          ),

          // 设置
          _buildNavItem(
            icon: Icons.settings_outlined,
            label: '设置',
            isActive: false,
            onTap: () {},
          ),
        ],
      ),
    );
  }

  /// 导航项
  Widget _buildNavItem({
    required IconData icon,
    required String label,
    required bool isActive,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: isActive
                  ? const Color(0xFF155DFC)
                  : const Color(0xFF99A1AF),
              size: 24,
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                fontSize: 16,
                color: isActive
                    ? const Color(0xFF155DFC)
                    : const Color(0xFF99A1AF),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
