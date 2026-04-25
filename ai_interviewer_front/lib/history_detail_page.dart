import 'package:flutter/material.dart';

/// AI 面试官助手 - 历史详情页面
/// 基于 Figma 设计实现
class HistoryDetailPage extends StatefulWidget {
  const HistoryDetailPage({super.key});

  @override
  State<HistoryDetailPage> createState() => _HistoryDetailPageState();
}

class _HistoryDetailPageState extends State<HistoryDetailPage> {
  final TextEditingController _searchController = TextEditingController();

  // 是否展开各区域
  bool _isDialogExpanded = false;

  // 模拟详情数据
  final InterviewDetail _detail = InterviewDetail(
    position: '前端开发工程师',
    date: '2024-12-10',
    time: '14:30',
    duration: '25分钟',
    dialogCount: 6,
    totalScore: 88,
    rating: '表现优秀',
    stageScores: [
      StageScore(name: '自我介绍', score: 8.5, maxScore: 10),
      StageScore(name: '项目经验', score: 8, maxScore: 10),
      StageScore(name: '技术问答', score: 9, maxScore: 10),
    ],
    strengths: [
      '回答清晰有条理，逻辑性强',
      '技术理解深入，能够举一反三',
      '沟通表达能力优秀，善于使用具体案例',
    ],
    improvements: [
      '可以更多地展示团队协作经验',
      '建议补充更多实际项目中的量化成果',
    ],
  );

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Column(
          children: [
            // 顶部导航栏
            _buildTopBar(context),

            // 主体内容
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 搜索框
                    _buildSearchBar(),
                    const SizedBox(height: 20),

                    // 总评分卡片
                    _buildScoreCard(),
                    const SizedBox(height: 24),

                    // 各环节得分
                    _buildStageScores(),
                    const SizedBox(height: 24),

                    // 优点
                    _buildStrengthsSection(),
                    const SizedBox(height: 16),

                    // 待改进
                    _buildImprovementsSection(),
                    const SizedBox(height: 16),

                    // 完整对话记录
                    _buildDialogSection(),
                    const SizedBox(height: 24),
                  ],
                ),
              ),
            ),

            // 底部按钮
            _buildBottomButtons(context),
          ],
        ),
      ),
    );
  }

  /// 顶部导航栏
  Widget _buildTopBar(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(
          bottom: BorderSide(
            color: Color(0xFFE5E7EB),
            width: 0.5,
          ),
        ),
      ),
      child: Row(
        children: [
          // 返回按钮
          GestureDetector(
            onTap: () => Navigator.pop(context),
            child: Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: const Color(0xFFF3F4F6),
                borderRadius: BorderRadius.circular(18),
              ),
              child: const Icon(
                Icons.arrow_back_ios_new,
                size: 16,
                color: Color(0xFF1E2939),
              ),
            ),
          ),
          const SizedBox(width: 12),

          // 标题
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _detail.position,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF1E2939),
                  ),
                ),
                Text(
                  '${_detail.date} ${_detail.time}',
                  style: const TextStyle(
                    fontSize: 12,
                    color: Color(0xFF6A7282),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// 搜索框
  Widget _buildSearchBar() {
    return Container(
      height: 48,
      decoration: BoxDecoration(
        color: const Color(0xFFF3F4F6),
        borderRadius: BorderRadius.circular(14),
      ),
      child: TextField(
        controller: _searchController,
        decoration: const InputDecoration(
          hintText: '搜索面试记录',
          hintStyle: TextStyle(
            fontSize: 16,
            color: Color(0xFF99A1AF),
          ),
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
    );
  }

  /// 总评分卡片
  Widget _buildScoreCard() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFFF9FAFB),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: const Color(0xFFE5E7EB),
          width: 1,
        ),
      ),
      child: Row(
        children: [
          // 分数圆圈
          Container(
            width: 64,
            height: 64,
            decoration: BoxDecoration(
              color: const Color(0xFF00C950),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  _detail.totalScore.toString(),
                  style: const TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 16),

          // 评分信息
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '总评分',
                  style: TextStyle(
                    fontSize: 14,
                    color: Color(0xFF6A7282),
                  ),
                ),
                Text(
                  _detail.rating,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF00C950),
                  ),
                ),
              ],
            ),
          ),

          // 日期、时长、对话数
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              _buildInfoItem(Icons.calendar_today, '日期', _detail.date),
              const SizedBox(height: 8),
              _buildInfoItem(Icons.access_time, '时长', _detail.duration),
              const SizedBox(height: 8),
              _buildInfoItem(Icons.chat_bubble_outline, '对话', '${_detail.dialogCount}条'),
            ],
          ),
        ],
      ),
    );
  }

  /// 信息项
  Widget _buildInfoItem(IconData icon, String label, String value) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          icon,
          size: 14,
          color: const Color(0xFF99A1AF),
        ),
        const SizedBox(width: 4),
        Text(
          '$label ',
          style: const TextStyle(
            fontSize: 12,
            color: Color(0xFF99A1AF),
          ),
        ),
        Text(
          value,
          style: const TextStyle(
            fontSize: 12,
            color: Color(0xFF4A5565),
          ),
        ),
      ],
    );
  }

  /// 各环节得分
  Widget _buildStageScores() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFFF9FAFB),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '各环节得分',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: Color(0xFF1E2939),
            ),
          ),
          const SizedBox(height: 16),
          ...List.generate(_detail.stageScores.length, (index) {
            final stage = _detail.stageScores[index];
            return Padding(
              padding: EdgeInsets.only(
                bottom: index < _detail.stageScores.length - 1 ? 16 : 0,
              ),
              child: _buildStageScoreItem(stage),
            );
          }),
        ],
      ),
    );
  }

  /// 环节得分项
  Widget _buildStageScoreItem(StageScore stage) {
    final progress = stage.score / stage.maxScore;
    Color progressColor;
    if (progress >= 0.8) {
      progressColor = const Color(0xFF00C950);
    } else if (progress >= 0.6) {
      progressColor = const Color(0xFF2B7FFF);
    } else {
      progressColor = const Color(0xFFF0B100);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              stage.name,
              style: const TextStyle(
                fontSize: 14,
                color: Color(0xFF4A5565),
              ),
            ),
            Text(
              '${stage.score.toStringAsFixed(1)}/${stage.maxScore.toInt()}',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: progressColor,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Container(
          height: 8,
          decoration: BoxDecoration(
            color: const Color(0xFFE5E7EB),
            borderRadius: BorderRadius.circular(4),
          ),
          child: FractionallySizedBox(
            alignment: Alignment.centerLeft,
            widthFactor: progress,
            child: Container(
              decoration: BoxDecoration(
                color: progressColor,
                borderRadius: BorderRadius.circular(4),
              ),
            ),
          ),
        ),
      ],
    );
  }

  /// 优点区域
  Widget _buildStrengthsSection() {
    return _buildListSection(
      icon: Icons.check_circle,
      iconColor: const Color(0xFF00C950),
      iconBgColor: const Color(0xFFE8F8ED),
      title: '优点',
      items: _detail.strengths,
    );
  }

  /// 待改进区域
  Widget _buildImprovementsSection() {
    return _buildListSection(
      icon: Icons.error,
      iconColor: const Color(0xFFF0B100),
      iconBgColor: const Color(0xFFFFF8E6),
      title: '待改进',
      items: _detail.improvements,
    );
  }

  /// 列表区域
  Widget _buildListSection({
    required IconData icon,
    required Color iconColor,
    required Color iconBgColor,
    required String title,
    required List<String> items,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: const Color(0xFFE5E7EB),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: iconBgColor,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(
                  icon,
                  size: 18,
                  color: iconColor,
                ),
              ),
              const SizedBox(width: 12),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF1E2939),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          ...items.map((item) {
            return Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 6,
                    height: 6,
                    margin: const EdgeInsets.only(top: 7),
                    decoration: BoxDecoration(
                      color: iconColor,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      item,
                      style: const TextStyle(
                        fontSize: 14,
                        color: Color(0xFF4A5565),
                        height: 1.5,
                      ),
                    ),
                  ),
                ],
              ),
            );
          }),
        ],
      ),
    );
  }

  /// 完整对话记录区域
  Widget _buildDialogSection() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: const Color(0xFFE5E7EB),
          width: 1,
        ),
      ),
      child: Column(
        children: [
          // 标题栏
          GestureDetector(
            onTap: () {
              setState(() {
                _isDialogExpanded = !_isDialogExpanded;
              });
            },
            child: Container(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  const Expanded(
                    child: Text(
                      '完整对话记录',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFF1E2939),
                      ),
                    ),
                  ),
                  Icon(
                    _isDialogExpanded
                        ? Icons.keyboard_arrow_up
                        : Icons.keyboard_arrow_down,
                    color: const Color(0xFF99A1AF),
                  ),
                ],
              ),
            ),
          ),

          // 对话内容
          if (_isDialogExpanded)
            Container(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: const Column(
                children: [
                  // 这里可以添加对话记录
                  Text(
                    '对话记录内容...',
                    style: TextStyle(
                      fontSize: 14,
                      color: Color(0xFF6A7282),
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  /// 底部按钮
  Widget _buildBottomButtons(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 10,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // 再练一次
          SizedBox(
            width: double.infinity,
            height: 50,
            child: ElevatedButton(
              onPressed: () {
                Navigator.pushNamed(context, '/upload');
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF2B7FFF),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                elevation: 0,
              ),
              child: const Text(
                '再练一次',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),

          // 分享面试结果
          SizedBox(
            width: double.infinity,
            height: 50,
            child: OutlinedButton(
              onPressed: () {
                // TODO: 分享功能
              },
              style: OutlinedButton.styleFrom(
                foregroundColor: const Color(0xFF6A7282),
                side: const BorderSide(
                  color: Color(0xFFE5E7EB),
                  width: 1.5,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text(
                '分享面试结果',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// 面试详情模型
class InterviewDetail {
  final String position;
  final String date;
  final String time;
  final String duration;
  final int dialogCount;
  final int totalScore;
  final String rating;
  final List<StageScore> stageScores;
  final List<String> strengths;
  final List<String> improvements;

  InterviewDetail({
    required this.position,
    required this.date,
    required this.time,
    required this.duration,
    required this.dialogCount,
    required this.totalScore,
    required this.rating,
    required this.stageScores,
    required this.strengths,
    required this.improvements,
  });
}

/// 环节得分模型
class StageScore {
  final String name;
  final double score;
  final double maxScore;

  StageScore({
    required this.name,
    required this.score,
    required this.maxScore,
  });
}
