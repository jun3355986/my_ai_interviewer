import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'design/app_design.dart';
import 'models/evaluation_report.dart';
import 'models/job.dart';
import 'services/interview_service.dart';

/// 评估报告：总评分环 + 各维度得分 + 优点/待改进 + 报告元信息。
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
  EvaluationReport? _report;
  late int _totalScore;
  List<StageScore> _stageScores = [];
  List<String> _strengths = [];
  List<String> _improvements = [];

  bool _isStrengthsExpanded = true;
  bool _isImprovementsExpanded = true;
  bool _isInitialized = false;
  bool _isLoadingReport = false;
  String? _reportError;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_isInitialized) return;
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );
    _totalScore = 0;
    _scoreAnimation = Tween<double>(begin: 0, end: 0).animate(
      CurvedAnimation(parent: _animationController, curve: Curves.easeOutCubic),
    );
    _isInitialized = true;

    final args = ModalRoute.of(context)?.settings.arguments;
    if (args is MatchResult) {
      _applyMatchResult(args);
    } else {
      _isLoadingReport = true;
      _report = context.read<InterviewService>().evaluationReport;
      unawaited(_loadPersistedResult(context.read<InterviewService>()));
    }
  }

  Future<void> _loadPersistedResult(InterviewService service) async {
    try {
      final result = await service.loadResult();
      if (!mounted) return;
      setState(() {
        _isLoadingReport = false;
        _report = service.evaluationReport;
        _applyMatchResult(result);
      });
    } catch (error) {
      if (!mounted) return;
      final message = error.toString().replaceFirst('Bad state: ', '');
      setState(() {
        _isLoadingReport = false;
        _reportError = message.contains('尚未完成')
            ? message
            : '评估报告暂时无法生成，请稍后重试。$message';
      });
    }
  }

  void _applyMatchResult(MatchResult result) {
    _matchResult = result;
    _totalScore = result.matchScore.toInt();
    _stageScores =
        result.matchDetails
            ?.map(
              (d) => StageScore(name: d.category, score: d.score, maxScore: 10),
            )
            .toList() ??
        [];
    _strengths =
        result.suggestions
            ?.where((suggestion) => !suggestion.startsWith('改进建议：'))
            .toList() ??
        [];
    _improvements =
        result.suggestions
            ?.where((suggestion) => suggestion.startsWith('改进建议：'))
            .toList() ??
        [];
    _scoreAnimation = Tween<double>(begin: 0, end: _totalScore.toDouble())
        .animate(
          CurvedAnimation(
            parent: _animationController,
            curve: Curves.easeOutCubic,
          ),
        );
    _animationController
      ..reset()
      ..forward();
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: Column(
          children: [
            AppTopBar(
              leading: BackButtonCircle(),
              title: '面试结果',
              subtitle: _report?.candidateName?.trim().isNotEmpty == true
                  ? '${_report!.candidateName}'
                  : null,
            ),
            Expanded(
              child: _isLoadingReport
                  ? const Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          CircularProgressIndicator(),
                          SizedBox(height: 14),
                          Text(
                            '正在读取持久化评估报告...',
                            style: TextStyle(color: AppColors.muted, fontSize: 13),
                          ),
                        ],
                      ),
                    )
                  : _reportError != null
                  ? EmptyState(
                      icon: Icons.assessment_outlined,
                      title: '暂无评估报告',
                      message: _reportError!,
                      actionLabel: '返回',
                      onAction: () => Navigator.maybePop(context),
                    )
                  : SingleChildScrollView(
                      padding: const EdgeInsets.all(20),
                      child: LayoutBuilder(
                        builder: (context, constraints) {
                          final body = Column(
                            children: [
                              _buildScoreHero(),
                              const SizedBox(height: 18),
                              if (_report != null) ...[
                                _buildReportMeta(),
                                const SizedBox(height: 18),
                              ],
                              _buildStageScores(),
                              const SizedBox(height: 16),
                              _buildStrengthsSection(),
                              const SizedBox(height: 14),
                              _buildImprovementsSection(),
                              const SizedBox(height: 22),
                              _buildActions(),
                            ],
                          );
                          if (constraints.maxWidth >= 860) {
                            return Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Expanded(flex: 13, child: body),
                                const SizedBox(width: 16),
                                Expanded(flex: 7, child: _buildSideColumn()),
                              ],
                            );
                          }
                          return body;
                        },
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSideColumn() {
    return Column(
      children: [
        if (_report?.summary.isNotEmpty == true)
          AppCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '评估摘要',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: AppColors.fg,
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  _report!.summary,
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.muted,
                    height: 1.6,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }

  Widget _buildScoreHero() {
    return AppCard(
      child: LayoutBuilder(
        builder: (context, constraints) {
          final ring = SizedBox(
            width: 150,
            height: 150,
            child: AnimatedBuilder(
              animation: _scoreAnimation,
              builder: (context, _) => CustomPaint(
                painter: _ScoreRingPainter(
                  progress: _scoreAnimation.value / 100,
                ),
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Text(
                        '总评分',
                        style: TextStyle(fontSize: 12, color: AppColors.muted),
                      ),
                      Text(
                        _scoreAnimation.value.toInt().toString(),
                        key: const Key('evaluation-total-score'),
                        style: const TextStyle(
                          fontSize: 40,
                          fontWeight: FontWeight.w700,
                          letterSpacing: -1,
                          color: AppColors.fg,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
          final summary = Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                _matchResult?.matchLevel ?? '评估已完成',
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w700,
                  color: AppColors.fg,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                '满分 100 分。分数来自持久化评估报告。',
                style: TextStyle(fontSize: 13, color: AppColors.muted, height: 1.5),
              ),
            ],
          );
          if (constraints.maxWidth >= 520) {
            return Row(
              children: [
                ring,
                const SizedBox(width: 32),
                Expanded(child: summary),
              ],
            );
          }
          return Column(
            children: [
              ring,
              const SizedBox(height: 18),
              summary,
            ],
          );
        },
      ),
    );
  }

  Widget _buildReportMeta() {
    final report = _report;
    if (report == null) return const SizedBox.shrink();
    final items = <(String, String)>[
      if (report.totalQuestions != null)
        ('答题数', '${report.answeredQuestions ?? 0}/${report.totalQuestions}'),
      if (report.durationMinutes != null)
        ('时长', '${report.durationMinutes} 分钟'),
      if (report.createdAt != null)
        ('评估时间', report.createdAt!.toLocal().fullDisplay),
    ];
    if (items.isEmpty) return const SizedBox.shrink();
    return AppCard(
      padding: const EdgeInsets.all(16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          for (final (label, value) in items)
            Column(
              children: [
                Text(
                  value,
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    color: AppColors.fg,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  label,
                  style: const TextStyle(fontSize: 12, color: AppColors.muted),
                ),
              ],
            ),
        ],
      ),
    );
  }

  Widget _buildStageScores() {
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '各环节得分',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w700,
              color: AppColors.fg,
            ),
          ),
          const SizedBox(height: 16),
          if (_stageScores.isEmpty)
            const Text('暂无环节评分', style: TextStyle(color: AppColors.muted))
          else
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

  Widget _buildStageScoreItem(StageScore stage) {
    final progress = (stage.score / stage.maxScore).clamp(0.0, 1.0);
    final progressColor = progress >= 0.8
        ? AppColors.success
        : progress >= 0.6
        ? AppColors.accent
        : AppColors.warn;

    return Column(
      children: [
        Row(
          children: [
            SizedBox(
              width: 76,
              child: Text(
                stage.name,
                style: const TextStyle(fontSize: 13, color: AppColors.fg2),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(child: AppMeter(value: progress, color: progressColor)),
            const SizedBox(width: 10),
            Text(
              '${stage.score.toStringAsFixed(1)}/${stage.maxScore.toInt()}',
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppColors.muted,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildStrengthsSection() {
    return _buildExpandableSection(
      icon: Icons.check_circle_outline,
      iconColor: AppColors.success,
      title: '优点',
      items: _strengths,
      isExpanded: _isStrengthsExpanded,
      onToggle: () =>
          setState(() => _isStrengthsExpanded = !_isStrengthsExpanded),
    );
  }

  Widget _buildImprovementsSection() {
    return _buildExpandableSection(
      icon: Icons.error_outline,
      iconColor: AppColors.warn,
      title: '待改进',
      items: _improvements,
      isExpanded: _isImprovementsExpanded,
      onToggle: () =>
          setState(() => _isImprovementsExpanded = !_isImprovementsExpanded),
    );
  }

  Widget _buildExpandableSection({
    required IconData icon,
    required Color iconColor,
    required String title,
    required List<String> items,
    required bool isExpanded,
    required VoidCallback onToggle,
  }) {
    return AppCard(
      padding: EdgeInsets.zero,
      child: Column(
        children: [
          InkWell(
            onTap: onToggle,
            borderRadius: const BorderRadius.vertical(
              top: Radius.circular(AppRadius.lg),
            ),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Container(
                    width: 30,
                    height: 30,
                    decoration: BoxDecoration(
                      color: iconColor.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(AppRadius.sm),
                    ),
                    child: Icon(icon, size: 17, color: iconColor),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      title,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: AppColors.fg,
                      ),
                    ),
                  ),
                  Icon(
                    isExpanded
                        ? Icons.keyboard_arrow_up
                        : Icons.keyboard_arrow_down,
                    color: AppColors.meta,
                  ),
                ],
              ),
            ),
          ),
          if (isExpanded)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: items.isEmpty
                  ? const Text('暂无内容', style: TextStyle(color: AppColors.muted))
                  : Column(
                      children: items
                          .map(
                            (item) => Padding(
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
                                        fontSize: 13,
                                        color: AppColors.muted,
                                        height: 1.5,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          )
                          .toList(),
                    ),
            ),
        ],
      ),
    );
  }

  Widget _buildActions() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AppButton(
          label: '查看详细对话',
          onPressed: _openTranscript,
        ),
        const SizedBox(height: 10),
        AppButton(
          label: '保存结果',
          variant: AppButtonVariant.secondary,
          icon: Icons.copy_outlined,
          onPressed: _copyReport,
        ),
        const SizedBox(height: 10),
        AppButton(
          label: '返回首页',
          variant: AppButtonVariant.ghost,
          onPressed: () =>
              Navigator.pushNamedAndRemoveUntil(context, '/home', (r) => false),
        ),
      ],
    );
  }

  void _openTranscript() {
    final service = context.read<InterviewService>();
    final lineageId = service.currentLineageId;
    final branchId =
        service.currentTranscript?.branchId ?? service.currentSessionId;
    if (lineageId == null || branchId == null) {
      showAppToast(context, '未找到当前面试谱系，请从面试历史进入回放');
      return;
    }
    Navigator.pushNamed(context, '/history-detail', arguments: {
      'lineageId': lineageId,
      'branchId': branchId,
    });
  }

  Future<void> _copyReport() async {
    final report = _report;
    final buffer = StringBuffer()
      ..writeln('AI 面试评估报告')
      ..writeln('总评分：$_totalScore / 100')
      ..writeln('推荐结论：${_matchResult?.matchLevel ?? ''}');
    if (report != null) {
      if (report.summary.isNotEmpty) buffer.writeln('总结：${report.summary}');
      if (report.strengths.isNotEmpty) buffer.writeln('优势：${report.strengths}');
      if (report.weaknesses.isNotEmpty) {
        buffer.writeln('待改进：${report.weaknesses}');
      }
    } else {
      for (final item in _strengths) {
        buffer.writeln(item);
      }
      for (final item in _improvements) {
        buffer.writeln(item);
      }
    }
    await Clipboard.setData(ClipboardData(text: buffer.toString()));
    if (mounted) showAppToast(context, '评估结果已复制到剪贴板');
  }
}

class StageScore {
  final String name;
  final double score;
  final double maxScore;

  StageScore({required this.name, required this.score, required this.maxScore});
}

class _ScoreRingPainter extends CustomPainter {
  _ScoreRingPainter({required this.progress});

  final double progress;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final strokeWidth = 13.0;
    final radius = (size.width - strokeWidth) / 2;

    final bgPaint = Paint()
      ..color = AppColors.surface
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth;
    canvas.drawCircle(center, radius, bgPaint);

    final progressPaint = Paint()
      ..color = AppColors.success
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -math.pi / 2,
      2 * math.pi * progress.clamp(0.0, 1.0),
      false,
      progressPaint,
    );
  }

  @override
  bool shouldRepaint(_ScoreRingPainter oldDelegate) =>
      oldDelegate.progress != progress;
}
