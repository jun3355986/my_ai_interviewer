import 'package:flutter/material.dart';
import 'dart:math' as math;
import 'models/job.dart';

/// AI 面试官助手 - 面试结果页面
/// 基于 Figma 设计实现
class InterviewResultPage extends StatefulWidget {
  const InterviewResultPage({super.key});

  @override
  State<InterviewResultPage> createState() => _InterviewResultPageState();
}

class _InterviewResultPageState extends State<InterviewResultPage>
    with SingleTickerProviderStateMixin {
  late AnimationController _animationController;
  late Animation<double> _scoreAnimation;

  MatchResult? _matchResult;
  late int _totalScore;
  List<StageScore> _stageScores = [];
  List<String> _strengths = [];
  List<String> _improvements = [];

  bool _isStrengthsExpanded = true;
  bool _isImprovementsExpanded = true;
  bool _isInitialized = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_isInitialized) {
      final args = ModalRoute.of(context)?.settings.arguments;
      if (args is MatchResult) {
        _matchResult = args;
        _totalScore = _matchResult!.matchScore.toInt();
        _stageScores =
            _matchResult!.matchDetails
                ?.map(
                  (d) => StageScore(
                    name: d.category,
                    score: d.score,
                    maxScore: 10,
                  ),
                )
                .toList() ??
            [];
        _strengths =
            _matchResult!.suggestions
                ?.where((s) => !s.contains('建议'))
                .toList() ??
            [];
        _improvements =
            _matchResult!.suggestions
                ?.where((s) => s.contains('建议'))
                .toList() ??
            [];
      } else {
        _totalScore = 0;
      }

      _animationController = AnimationController(
        vsync: this,
        duration: const Duration(milliseconds: 1500),
      );
      _scoreAnimation = Tween<double>(begin: 0, end: _totalScore.toDouble())
          .animate(
            CurvedAnimation(
              parent: _animationController,
              curve: Curves.easeOutCubic,
            ),
          );
      _animationController.forward();
      _isInitialized = true;
    }
  }

  @override
  void initState() {
    super.initState();
    // Animation controller initialized in didChangeDependencies
  }

  @override
  void dispose() {
    _animationController.dispose();
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
                  children: [
                    // 恭喜区域
                    _buildCongratulationSection(),
                    const SizedBox(height: 32),

                    // 总评分
                    _buildTotalScoreSection(),
                    const SizedBox(height: 32),

                    // 各环节得分
                    _buildStageScoresSection(),
                    const SizedBox(height: 24),

                    // 优点
                    _buildStrengthsSection(),
                    const SizedBox(height: 16),

                    // 待改进
                    _buildImprovementsSection(),
                    const SizedBox(height: 32),
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
          bottom: BorderSide(color: Color(0xFFE5E7EB), width: 0.5),
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
          const Text(
            '面试结果',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: Color(0xFF1E2939),
            ),
          ),
        ],
      ),
    );
  }

  /// 恭喜区域
  Widget _buildCongratulationSection() {
    return Column(
      children: [
        // 奖杯图标
        Container(
          width: 80,
          height: 80,
          decoration: BoxDecoration(
            color: const Color(0xFFFFF3E0),
            borderRadius: BorderRadius.circular(20),
          ),
          child: const Icon(
            Icons.emoji_events,
            size: 48,
            color: Color(0xFFFFB300),
          ),
        ),
        const SizedBox(height: 20),

        // 恭喜文字
        const Text(
          '恭喜完成面试！',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: Color(0xFF1E2939),
          ),
        ),
        const SizedBox(height: 8),
        const Text(
          '您的表现非常出色',
          style: TextStyle(fontSize: 15, color: Color(0xFF6A7282)),
        ),
      ],
    );
  }

  /// 总评分区域
  Widget _buildTotalScoreSection() {
    return AnimatedBuilder(
      animation: _scoreAnimation,
      builder: (context, child) {
        return Column(
          children: [
            // 环形进度
            SizedBox(
              width: 160,
              height: 160,
              child: Stack(
                alignment: Alignment.center,
                children: [
                  // 背景圆环
                  CustomPaint(
                    size: const Size(160, 160),
                    painter: CircleProgressPainter(
                      progress: _scoreAnimation.value / 100,
                      backgroundColor: const Color(0xFFE5E7EB),
                      progressColor: const Color(0xFF00C950),
                      strokeWidth: 12,
                    ),
                  ),
                  // 分数
                  Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Text(
                        '总评分',
                        style: TextStyle(
                          fontSize: 14,
                          color: Color(0xFF6A7282),
                        ),
                      ),
                      Text(
                        _scoreAnimation.value.toInt().toString(),
                        style: const TextStyle(
                          fontSize: 48,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF1E2939),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              '满分 100 分',
              style: TextStyle(fontSize: 13, color: Color(0xFF99A1AF)),
            ),
          ],
        );
      },
    );
  }

  /// 各环节得分
  Widget _buildStageScoresSection() {
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
          ...List.generate(_stageScores.length, (index) {
            final stage = _stageScores[index];
            return Padding(
              padding: EdgeInsets.only(
                bottom: index < _stageScores.length - 1 ? 16 : 0,
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
              style: const TextStyle(fontSize: 14, color: Color(0xFF4A5565)),
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
    return _buildExpandableSection(
      icon: Icons.check_circle,
      iconColor: const Color(0xFF00C950),
      iconBgColor: const Color(0xFFE8F8ED),
      title: '优点',
      items: _strengths,
      isExpanded: _isStrengthsExpanded,
      onToggle: () {
        setState(() {
          _isStrengthsExpanded = !_isStrengthsExpanded;
        });
      },
    );
  }

  /// 待改进区域
  Widget _buildImprovementsSection() {
    return _buildExpandableSection(
      icon: Icons.error,
      iconColor: const Color(0xFFF0B100),
      iconBgColor: const Color(0xFFFFF8E6),
      title: '待改进',
      items: _improvements,
      isExpanded: _isImprovementsExpanded,
      onToggle: () {
        setState(() {
          _isImprovementsExpanded = !_isImprovementsExpanded;
        });
      },
    );
  }

  /// 可展开区域
  Widget _buildExpandableSection({
    required IconData icon,
    required Color iconColor,
    required Color iconBgColor,
    required String title,
    required List<String> items,
    required bool isExpanded,
    required VoidCallback onToggle,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE5E7EB), width: 1),
      ),
      child: Column(
        children: [
          // 标题栏
          GestureDetector(
            onTap: onToggle,
            child: Container(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: iconBgColor,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(icon, size: 18, color: iconColor),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      title,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFF1E2939),
                      ),
                    ),
                  ),
                  Icon(
                    isExpanded
                        ? Icons.keyboard_arrow_up
                        : Icons.keyboard_arrow_down,
                    color: const Color(0xFF99A1AF),
                  ),
                ],
              ),
            ),
          ),

          // 内容
          if (isExpanded)
            Container(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: Column(
                children: items.map((item) {
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
                }).toList(),
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
          // 查看详细对话
          SizedBox(
            width: double.infinity,
            height: 50,
            child: ElevatedButton(
              onPressed: () {
                // TODO: 查看详细对话
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
                '查看详细对话',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
              ),
            ),
          ),
          const SizedBox(height: 12),

          // 保存结果和返回首页
          Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 50,
                  child: OutlinedButton.icon(
                    onPressed: () {
                      // TODO: 保存结果
                    },
                    icon: const Icon(Icons.download, size: 18),
                    label: const Text('保存结果'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFF2B7FFF),
                      side: const BorderSide(
                        color: Color(0xFF2B7FFF),
                        width: 1.5,
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: SizedBox(
                  height: 50,
                  child: OutlinedButton.icon(
                    onPressed: () {
                      Navigator.pushNamedAndRemoveUntil(
                        context,
                        '/home',
                        (route) => false,
                      );
                    },
                    icon: const Icon(Icons.home, size: 18),
                    label: const Text('返回首页'),
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
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// 环节得分模型
class StageScore {
  final String name;
  final double score;
  final double maxScore;

  StageScore({required this.name, required this.score, required this.maxScore});
}

/// 圆形进度绘制器
class CircleProgressPainter extends CustomPainter {
  final double progress;
  final Color backgroundColor;
  final Color progressColor;
  final double strokeWidth;

  CircleProgressPainter({
    required this.progress,
    required this.backgroundColor,
    required this.progressColor,
    required this.strokeWidth,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = (size.width - strokeWidth) / 2;

    // 背景圆环
    final bgPaint = Paint()
      ..color = backgroundColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;

    canvas.drawCircle(center, radius, bgPaint);

    // 进度圆环
    final progressPaint = Paint()
      ..color = progressColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;

    final sweepAngle = 2 * math.pi * progress;
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -math.pi / 2,
      sweepAngle,
      false,
      progressPaint,
    );
  }

  @override
  bool shouldRepaint(CircleProgressPainter oldDelegate) {
    return oldDelegate.progress != progress;
  }
}
