import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'design/app_design.dart';
import 'models/job.dart';
import 'services/interview_service.dart';
import 'services/job_service.dart';
import 'services/resume_service.dart';

/// 上传简历页：PDF 上传 + 目标岗位选择，继续/跳过后进入面试工作台。
class UploadResumePage extends StatefulWidget {
  const UploadResumePage({super.key});

  @override
  State<UploadResumePage> createState() => _UploadResumePageState();
}

class _UploadResumePageState extends State<UploadResumePage> {
  bool _hasUploadedFile = false;
  String? _uploadedFileName;
  int? _resumeId;
  int? _jobId;
  bool _starting = false;
  bool _routeInitialized = false;

  List<Job> _jobs = const [];
  bool _loadingJobs = true;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_routeInitialized) return;
    _routeInitialized = true;
    final arguments = ModalRoute.of(context)?.settings.arguments;
    final rawJobId = arguments is Map ? arguments['jobId'] : arguments;
    _jobId = switch (rawJobId) {
      int value => value,
      String value => int.tryParse(value),
      _ => null,
    };
    _loadJobs();
  }

  Future<void> _loadJobs() async {
    // JobService 在部分测试/宿主中可能未注入，缺省时退化为不展示岗位选择。
    final jobService = _lookupJobService();
    if (jobService == null) {
      if (mounted) setState(() => _loadingJobs = false);
      return;
    }
    final jobs = await jobService.getJobs();
    if (!mounted) return;
    setState(() {
      _jobs = jobs;
      _loadingJobs = false;
    });
  }

  JobService? _lookupJobService() {
    try {
      return context.read<JobService>();
    } catch (_) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final resumeService = context.watch<ResumeService>();

    return Scaffold(
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: Column(
          children: [
            AppTopBar(
              leading: BackButtonCircle(onTap: () => Navigator.pop(context)),
              title: '上传简历',
              subtitle: '新面试 · 步骤 1 / 2',
            ),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(20),
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    final dropZone = _buildUploadArea(resumeService);
                    final sideCard = _buildSideColumn();
                    if (constraints.maxWidth >= 820) {
                      return Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(flex: 13, child: dropZone),
                          const SizedBox(width: 16),
                          Expanded(flex: 7, child: sideCard),
                        ],
                      );
                    }
                    return Column(
                      children: [
                        dropZone,
                        const SizedBox(height: 16),
                        sideCard,
                      ],
                    );
                  },
                ),
              ),
            ),
            _buildBottomButtons(),
          ],
        ),
      ),
    );
  }

  Widget _buildUploadArea(ResumeService resumeService) {
    return GestureDetector(
      onTap: resumeService.isLoading || _starting ? null : _handleUpload,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        width: double.infinity,
        constraints: const BoxConstraints(minHeight: 300),
        padding: const EdgeInsets.symmetric(vertical: 44, horizontal: 24),
        decoration: BoxDecoration(
          color: _hasUploadedFile
              ? AppColors.accent.withValues(alpha: 0.04)
              : AppColors.surfaceWarm,
          borderRadius: BorderRadius.circular(AppRadius.md),
          border: Border.all(
            color: _hasUploadedFile
                ? AppColors.accent
                : AppColors.border,
            width: 1.4,
          ),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: AppColors.accentSoft,
                borderRadius: BorderRadius.circular(AppRadius.md),
              ),
              child: Icon(
                _hasUploadedFile
                    ? Icons.description_outlined
                    : Icons.upload_file_outlined,
                size: 28,
                color: AppColors.accent,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              _hasUploadedFile ? _uploadedFileName ?? '已上传文件' : '点击上传 PDF 简历',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w600,
                color: _hasUploadedFile ? AppColors.accent : AppColors.fg,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              _hasUploadedFile ? '点击可重新上传' : '或拖拽文件到此处',
              style: const TextStyle(fontSize: 13, color: AppColors.muted),
            ),
            const SizedBox(height: 8),
            const Text(
              '支持 PDF 格式，最大 10MB',
              style: TextStyle(fontSize: 12, color: AppColors.meta),
            ),
            if (resumeService.isLoading)
              const Padding(
                padding: EdgeInsets.only(top: 16),
                child: SizedBox.square(
                  dimension: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildSideColumn() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '上传提示',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: AppColors.fg,
                ),
              ),
              const SizedBox(height: 14),
              _buildTipItem(
                '请确保简历内容清晰完整，包含教育背景和工作经验。',
                AppColors.accent,
              ),
              const SizedBox(height: 12),
              _buildTipItem('AI 将分析您的简历并生成针对性的面试问题。', AppColors.success),
              const SizedBox(height: 12),
              _buildTipItem('您的简历信息仅用于面试练习，我们会严格保护隐私。', AppColors.warn),
            ],
          ),
        ),
        const SizedBox(height: 16),
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '目标岗位',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: AppColors.fg,
                ),
              ),
              const SizedBox(height: 14),
              const AppFieldLabel('选择岗位'),
              const SizedBox(height: 6),
              _buildJobSelector(),
              const SizedBox(height: 10),
              const Text(
                '岗位来自职位服务；不指定岗位时使用通用面试大纲。',
                style: TextStyle(fontSize: 12, color: AppColors.meta),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildJobSelector() {
    if (_loadingJobs) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: SizedBox.square(
          dimension: 18,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }
    if (_jobs.isEmpty) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(AppRadius.sm),
          border: Border.all(color: AppColors.border),
        ),
        child: const Text(
          '暂无可用岗位，将使用通用面试大纲',
          style: TextStyle(fontSize: 13, color: AppColors.muted),
        ),
      );
    }
    final selectedJob = _jobs
        .where((job) => int.tryParse(job.id ?? '') == _jobId)
        .firstOrNull;
    return GestureDetector(
      onTap: () => _showJobPicker(),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(AppRadius.sm),
          border: Border.all(color: AppColors.border),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                selectedJob == null
                    ? '不指定岗位'
                    : selectedJob.title + (selectedJob.company != null ? ' · ${selectedJob.company}' : ''),
                style: const TextStyle(fontSize: 14, color: AppColors.fg),
              ),
            ),
            const Icon(Icons.unfold_more, size: 18, color: AppColors.meta),
          ],
        ),
      ),
    );
  }

  void _showJobPicker() {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: AppColors.bg,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.lg)),
      ),
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  const Text(
                    '选择目标岗位',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: AppColors.fg,
                    ),
                  ),
                  const Spacer(),
                  AppButton(
                    label: '不指定',
                    small: true,
                    variant: AppButtonVariant.ghost,
                    onPressed: () {
                      setState(() => _jobId = null);
                      Navigator.pop(sheetContext);
                    },
                  ),
                ],
              ),
            ),
            const Divider(height: 1, color: AppColors.borderSoft),
            Flexible(
              child: ListView(
                shrinkWrap: true,
                padding: const EdgeInsets.symmetric(vertical: 8),
                children: [
                  for (final job in _jobs)
                    ListTile(
                      title: Text(
                        job.title,
                        style: const TextStyle(fontSize: 14, color: AppColors.fg),
                      ),
                      subtitle: job.company == null
                          ? null
                          : Text(
                              job.company!,
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.muted,
                              ),
                            ),
                      trailing: int.tryParse(job.id ?? '') == _jobId
                          ? const Icon(Icons.check, size: 20, color: AppColors.accent)
                          : null,
                      onTap: () {
                        setState(() => _jobId = int.tryParse(job.id ?? ''));
                        Navigator.pop(sheetContext);
                      },
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTipItem(String text, Color dotColor) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 7,
          height: 7,
          margin: const EdgeInsets.only(top: 6),
          decoration: BoxDecoration(color: dotColor, shape: BoxShape.circle),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(
              fontSize: 13,
              color: AppColors.muted,
              height: 1.5,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildBottomButtons() {
    final resumeService = context.watch<ResumeService>();
    return Container(
      padding: EdgeInsets.fromLTRB(
        20,
        16,
        20,
        16 + MediaQuery.paddingOf(context).bottom,
      ),
      decoration: const BoxDecoration(
        color: AppColors.bg,
        border: Border(top: BorderSide(color: AppColors.borderSoft)),
      ),
      child: Row(
        children: [
          Expanded(
            child: AppButton(
              keyOverride: const Key('skip-resume-start'),
              label: _starting ? '正在创建面试...' : '跳过此步骤',
              variant: AppButtonVariant.ghost,
              onPressed: _starting ? null : () => _navigateToChat(skip: true),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            flex: 2,
            child: AppButton(
              keyOverride: const Key('start-with-resume'),
              label: _starting
                  ? '正在创建面试...'
                  : _hasUploadedFile
                  ? '继续'
                  : '请先上传简历',
              onPressed: _hasUploadedFile && !_starting && !resumeService.isLoading
                  ? _navigateToChat
                  : null,
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _handleUpload() async {
    final resumeService = context.read<ResumeService>();

    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['pdf'],
      withData: true,
    );

    if (result == null || result.files.isEmpty) return;
    final file = result.files.single;
    if (file.size > 10 * 1024 * 1024) {
      if (mounted) showAppToast(context, '简历不能超过 10MB');
      return;
    }

    if (!mounted) return;

    final uploadedResumeId = await resumeService.uploadResume(file);
    final resumeId = uploadedResumeId == null
        ? null
        : int.tryParse(uploadedResumeId);

    if (resumeId != null) {
      // 上传成功后立即解析：原文落库 raw_text，真实面试与模拟面试都依赖它。
      final parsed = await resumeService.parseResume(uploadedResumeId!);
      if (mounted) {
        setState(() {
          _hasUploadedFile = true;
          _uploadedFileName = file.name;
          _resumeId = resumeId;
        });
        showAppToast(
          context,
          parsed ? '上传成功' : '上传成功，但解析失败，AI 面试将使用通用大纲',
        );
      }
    } else {
      if (mounted) {
        showAppToast(
          context,
          resumeService.error ?? (uploadedResumeId == null ? '上传失败' : '简历 ID 格式无效'),
        );
      }
    }
  }

  Future<void> _navigateToChat({bool skip = false}) async {
    if (_starting) return;

    var useMock = false;
    if (!skip) {
      final picked = await _pickInterviewMode();
      if (picked == null || !mounted) return; // 用户取消选择
      useMock = picked;
      if (useMock && _resumeId == null) {
        showAppToast(context, '模拟面试需要先成功上传简历');
        return;
      }
    }

    setState(() => _starting = true);
    final interviewService = context.read<InterviewService>();
    try {
      await interviewService.startNewInterview(
        resumeId: skip ? null : _resumeId,
        jobId: _jobId,
      );
      if (!mounted) return;
      Navigator.pushNamed(
        context,
        '/chat',
        arguments: skip
            ? null
            : {'autoDrive': useMock, 'resumeId': _resumeId, 'jobId': _jobId},
      );
    } catch (error) {
      if (!mounted) return;
      final message = error.toString().replaceFirst('Bad state: ', '');
      showAppToast(context, '创建面试失败：$message');
    } finally {
      if (mounted) setState(() => _starting = false);
    }
  }

  /// 进入面试前选择模式；返回 null 表示取消。
  Future<bool?> _pickInterviewMode() {
    return showModalBottomSheet<bool>(
      context: context,
      backgroundColor: AppColors.bg,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.lg)),
      ),
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 18, 16, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '选择面试模式',
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  color: AppColors.fg,
                ),
              ),
              const SizedBox(height: 6),
              const Text(
                '两种模式都走完整面试流程，记录均可回放与评估。',
                style: TextStyle(fontSize: 12, color: AppColors.muted),
              ),
              const SizedBox(height: 14),
              _buildModeCard(
                sheetContext,
                value: false,
                icon: Icons.person_outline,
                title: '真实面试',
                subtitle: '你亲自回答面试官提问（原流程）。',
              ),
              const SizedBox(height: 10),
              _buildModeCard(
                sheetContext,
                value: true,
                icon: Icons.smart_toy_outlined,
                title: '模拟面试',
                subtitle: 'AI 候选人代替你作答，自动走完面试全流程（上限 25 分钟），便于快速观察与测试。',
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildModeCard(
    BuildContext sheetContext, {
    required bool value,
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    return GestureDetector(
      onTap: () => Navigator.pop(sheetContext, value),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(AppRadius.md),
          border: Border.all(color: AppColors.border),
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: AppColors.accentSoft,
                borderRadius: BorderRadius.circular(AppRadius.sm),
              ),
              child: Icon(icon, size: 22, color: AppColors.accent),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: AppColors.fg,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    subtitle,
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.muted,
                      height: 1.4,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, size: 18, color: AppColors.meta),
          ],
        ),
      ),
    );
  }
}
