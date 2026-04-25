import 'package:flutter/material.dart';

/// AI 面试官助手 - 面试历史页面
/// 基于 Figma 设计实现
class InterviewHistoryPage extends StatefulWidget {
  const InterviewHistoryPage({super.key});

  @override
  State<InterviewHistoryPage> createState() => _InterviewHistoryPageState();
}

class _InterviewHistoryPageState extends State<InterviewHistoryPage> {
  final TextEditingController _searchController = TextEditingController();

  // 排序方式
  String _sortBy = 'time'; // 'time' or 'score'

  // 模拟历史数据
  final List<InterviewRecord> _records = [
    InterviewRecord(
      id: '1',
      position: '前端开发工程师',
      date: '2024-12-10',
      time: '14:30',
      duration: '25分钟',
      score: 88,
      rating: '优秀',
    ),
    InterviewRecord(
      id: '2',
      position: '产品经理',
      date: '2024-12-08',
      time: '10:15',
      duration: '30分钟',
      score: 82,
      rating: '良好',
    ),
    InterviewRecord(
      id: '3',
      position: 'UI设计师',
      date: '2024-12-05',
      time: '16:45',
      duration: '20分钟',
      score: 90,
      rating: '优秀',
    ),
    InterviewRecord(
      id: '4',
      position: '全栈开发工程师',
      date: '2024-12-03',
      time: '09:00',
      duration: '35分钟',
      score: 75,
      rating: '良好',
    ),
    InterviewRecord(
      id: '5',
      position: '数据分析师',
      date: '2024-12-01',
      time: '15:20',
      duration: '28分钟',
      score: 68,
      rating: '一般',
    ),
  ];

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF9FAFB),
      body: SafeArea(
        child: Column(
          children: [
            // 顶部区域
            _buildHeader(),

            // 历史列表
            Expanded(child: _buildRecordsList()),

            // 底部导航栏
            _buildBottomNavigationBar(context),
          ],
        ),
      ),
      // 悬浮按钮
      // floatingActionButton: _buildFloatingActionButton(context),
    );
  }

  /// 顶部区域
  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(
          bottom: BorderSide(color: Color(0xFFE5E7EB), width: 0.5),
        ),
      ),
      child: Column(
        children: [
          // 标题栏
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                '面试历史',
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF1E2939),
                ),
              ),
              GestureDetector(
                onTap: () {
                  // TODO: 搜索功能
                },
                child: Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF3F4F6),
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: const Icon(
                    Icons.search,
                    size: 20,
                    color: Color(0xFF6A7282),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),

          // 搜索框
          Container(
            height: 48,
            decoration: BoxDecoration(
              color: const Color(0xFFF3F4F6),
              borderRadius: BorderRadius.circular(14),
            ),
            child: TextField(
              controller: _searchController,
              decoration: const InputDecoration(
                hintText: '搜索面试记录',
                hintStyle: TextStyle(fontSize: 16, color: Color(0xFF99A1AF)),
                prefixIcon: Icon(
                  Icons.search,
                  size: 20,
                  color: Color(0xFF99A1AF),
                ),
                border: InputBorder.none,
                contentPadding: EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 14,
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // 排序按钮
          Row(
            children: [
              _buildSortButton('按时间', 'time'),
              const SizedBox(width: 8),
              _buildSortButton('按评分', 'score'),
            ],
          ),
        ],
      ),
    );
  }

  /// 排序按钮
  Widget _buildSortButton(String label, String value) {
    final isSelected = _sortBy == value;
    return GestureDetector(
      onTap: () {
        setState(() {
          _sortBy = value;
        });
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        decoration: BoxDecoration(
          color: isSelected ? const Color(0xFF2B7FFF) : const Color(0xFFF3F4F6),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            color: isSelected ? Colors.white : const Color(0xFF4A5565),
          ),
        ),
      ),
    );
  }

  /// 历史列表
  Widget _buildRecordsList() {
    return ListView.separated(
      padding: const EdgeInsets.all(24),
      itemCount: _records.length,
      separatorBuilder: (context, index) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        return _buildRecordItem(_records[index]);
      },
    );
  }

  /// 记录项
  Widget _buildRecordItem(InterviewRecord record) {
    // 根据评级获取颜色
    Color ratingColor;
    switch (record.rating) {
      case '优秀':
        ratingColor = const Color(0xFF00C950);
        break;
      case '良好':
        ratingColor = const Color(0xFF2B7FFF);
        break;
      default:
        ratingColor = const Color(0xFFF0B100);
    }

    return GestureDetector(
      onTap: () {
        Navigator.pushNamed(context, '/history-detail', arguments: record);
      },
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.05),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          children: [
            // 左侧信息
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 职位和评级
                  Row(
                    children: [
                      Text(
                        record.position,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFF1E2939),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: ratingColor,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          record.rating,
                          style: const TextStyle(
                            fontSize: 12,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),

                  // 日期、时间、时长
                  Row(
                    children: [
                      Text(
                        record.date,
                        style: const TextStyle(
                          fontSize: 14,
                          color: Color(0xFF6A7282),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Text(
                        record.time,
                        style: const TextStyle(
                          fontSize: 14,
                          color: Color(0xFF6A7282),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Text(
                        record.duration,
                        style: const TextStyle(
                          fontSize: 14,
                          color: Color(0xFF6A7282),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            // 右侧分数和箭头
            Row(
              children: [
                Column(
                  children: [
                    Text(
                      record.score.toString(),
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF1E2939),
                      ),
                    ),
                    const Text(
                      '分',
                      style: TextStyle(fontSize: 14, color: Color(0xFF99A1AF)),
                    ),
                  ],
                ),
                const SizedBox(width: 8),
                const Icon(
                  Icons.chevron_right,
                  color: Color(0xFF99A1AF),
                  size: 24,
                ),
              ],
            ),
          ],
        ),
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
          top: BorderSide(color: Color(0xFFE5E7EB), width: 0.5),
        ),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _buildNavItem(
            icon: Icons.home_outlined,
            label: '首页',
            isActive: false,
            onTap: () => Navigator.pushReplacementNamed(context, '/home'),
          ),
          _buildNavItem(
            icon: Icons.chat_bubble_outline,
            label: '面试',
            isActive: false,
            onTap: () => Navigator.pushNamed(context, '/upload'),
          ),
          _buildNavItem(
            icon: Icons.history,
            label: '历史',
            isActive: true,
            onTap: () {},
          ),
          _buildNavItem(
            icon: Icons.settings_outlined,
            label: '设置',
            isActive: false,
            onTap: () {
              // TODO: 设置页面
            },
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
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            icon,
            size: 24,
            color: isActive ? const Color(0xFF155DFC) : const Color(0xFF99A1AF),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: isActive
                  ? const Color(0xFF155DFC)
                  : const Color(0xFF99A1AF),
            ),
          ),
        ],
      ),
    );
  }

  /// 悬浮按钮
  // Widget _buildFloatingActionButton(BuildContext context) {
  //   return Container(
  //     width: 56,
  //     height: 56,
  //     decoration: BoxDecoration(
  //       gradient: const LinearGradient(
  //         colors: [Color(0xFF2B7FFF), Color(0xFF4F39F6)],
  //         begin: Alignment.topLeft,
  //         end: Alignment.bottomRight,
  //       ),
  //       shape: BoxShape.circle,
  //       boxShadow: [
  //         BoxShadow(
  //           color: const Color(0xFF2B7FFF).withValues(alpha: 0.4),
  //           blurRadius: 12,
  //           offset: const Offset(0, 4),
  //         ),
  //       ],
  //     ),
  //     child: Material(
  //       color: Colors.transparent,
  //       child: InkWell(
  //         onTap: () => Navigator.pushNamed(context, '/upload'),
  //         borderRadius: BorderRadius.circular(28),
  //         child: const Icon(
  //           Icons.add,
  //           color: Colors.white,
  //           size: 28,
  //         ),
  //       ),
  //     ),
  //   );
  // }
}

/// 面试记录模型
class InterviewRecord {
  final String id;
  final String position;
  final String date;
  final String time;
  final String duration;
  final int score;
  final String rating;

  InterviewRecord({
    required this.id,
    required this.position,
    required this.date,
    required this.time,
    required this.duration,
    required this.score,
    required this.rating,
  });
}
