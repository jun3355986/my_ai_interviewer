import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'design/app_design.dart';
import 'models/user.dart';
import 'services/auth_service.dart';
import 'services/notification_service.dart';

enum _SettingsTab { account, notify }

/// 设置页：账号资料（getMe/updateMe）、通知偏好（通知服务）、退出登录。
class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  _SettingsTab _tab = _SettingsTab.account;

  User? _user;
  bool _loadingUser = true;
  bool _savingProfile = false;

  late final TextEditingController _nicknameController = TextEditingController();
  late final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _emailController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadUser();
    context.read<NotificationService>().loadPreference();
  }

  @override
  void dispose() {
    _nicknameController.dispose();
    _phoneController.dispose();
    _usernameController.dispose();
    _emailController.dispose();
    super.dispose();
  }

  Future<void> _loadUser() async {
    final user = await context.read<AuthService>().getMe();
    if (!mounted) return;
    setState(() {
      _user = user;
      _nicknameController.text = user?.nickname ?? '';
      _phoneController.text = user?.phone ?? '';
      _usernameController.text = user?.username ?? '';
      _emailController.text = user?.email ?? '';
      _loadingUser = false;
    });
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
              title: '设置',
              subtitle: '账号资料、通知与退出',
            ),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(20),
                child: Column(
                  children: [
                    AppSegmentedControl<_SettingsTab>(
                      segments: const [
                        (_SettingsTab.account, '账号资料'),
                        (_SettingsTab.notify, '通知'),
                      ],
                      selected: _tab,
                      onChanged: (value) => setState(() => _tab = value),
                    ),
                    const SizedBox(height: 16),
                    if (_tab == _SettingsTab.account)
                      _buildAccountPanel()
                    else
                      _buildNotifyPanel(),
                  ],
                ),
              ),
            ),
            AppBottomNav(
              current: 'settings',
              onSelect: _onNavSelect,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAccountPanel() {
    if (_loadingUser) {
      return const AppCard(
        child: Center(child: CircularProgressIndicator()),
      );
    }
    final user = _user;
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              UserAvatar(
                nickname: user?.nickname,
                username: user?.username,
                avatarUrl: user?.avatarUrl,
                size: 46,
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user?.nickname?.trim().isNotEmpty == true
                          ? user!.nickname!
                          : user?.username ?? '未登录',
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.fg,
                      ),
                    ),
                    if (user?.roles?.isNotEmpty == true)
                      Text(
                        '角色：${user!.roles!.join(' / ')}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.muted,
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          const AppFieldLabel('用户名（不可修改）'),
          const SizedBox(height: 6),
          TextField(
            controller: _usernameController,
            readOnly: true,
            decoration: appInputDecoration(),
          ),
          const SizedBox(height: 14),
          const AppFieldLabel('邮箱（不可修改）'),
          const SizedBox(height: 6),
          TextField(
            controller: _emailController,
            readOnly: true,
            decoration: appInputDecoration(),
          ),
          const SizedBox(height: 14),
          const AppFieldLabel('昵称'),
          const SizedBox(height: 6),
          TextField(
            controller: _nicknameController,
            decoration: appInputDecoration(hint: '展示在首页与设置页'),
          ),
          const SizedBox(height: 14),
          const AppFieldLabel('手机号'),
          const SizedBox(height: 6),
          TextField(
            controller: _phoneController,
            keyboardType: TextInputType.phone,
            decoration: appInputDecoration(hint: '可选'),
          ),
          const SizedBox(height: 20),
          Row(
            children: [
              AppButton(
                label: _savingProfile ? '正在保存…' : '保存资料',
                loading: _savingProfile,
                onPressed: _savingProfile ? null : _saveProfile,
              ),
              const SizedBox(width: 10),
              AppButton(
                label: '退出登录',
                variant: AppButtonVariant.secondary,
                onPressed: _logout,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _saveProfile() async {
    setState(() => _savingProfile = true);
    try {
      final updated = await context.read<AuthService>().updateMe(
            nickname: _nicknameController.text.trim(),
            phone: _phoneController.text.trim(),
          );
      if (!mounted) return;
      if (updated != null) {
        setState(() => _user = updated);
        showAppToast(context, '资料已保存');
      } else {
        showAppToast(context, '保存失败，请稍后重试');
      }
    } finally {
      if (mounted) setState(() => _savingProfile = false);
    }
  }

  Future<void> _logout() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.lg),
        ),
        title: const Text('退出登录'),
        content: const Text(
          '退出后将清除本地登录状态，需要重新登录才能继续练习。',
          style: TextStyle(color: AppColors.muted, height: 1.5),
        ),
        actions: [
          AppButton(
            label: '取消',
            small: true,
            variant: AppButtonVariant.secondary,
            onPressed: () => Navigator.pop(dialogContext, false),
          ),
          AppButton(
            label: '退出登录',
            small: true,
            variant: AppButtonVariant.danger,
            onPressed: () => Navigator.pop(dialogContext, true),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await context.read<AuthService>().logout();
    if (!mounted) return;
    Navigator.pushNamedAndRemoveUntil(context, '/login', (route) => false);
  }

  Widget _buildNotifyPanel() {
    final service = context.watch<NotificationService>();
    final preference = service.preference;
    return AppCard(
      child: preference == null
          ? const Center(
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: 20),
                child: CircularProgressIndicator(),
              ),
            )
          : Column(
              children: [
                _buildSwitchRow(
                  title: '面试进度通知',
                  description: '后台生成完成时提醒。',
                  value: preference.progressNotify,
                  saving: service.savingPreference,
                  onChanged: (value) => service.savePreference(
                    preference.copyWith(progressNotify: value),
                  ),
                ),
                const Divider(height: 1, color: AppColors.borderSoft),
                _buildSwitchRow(
                  title: '评估完成通知',
                  description: '报告生成后提醒查看。',
                  value: preference.evaluationNotify,
                  saving: service.savingPreference,
                  onChanged: (value) => service.savePreference(
                    preference.copyWith(evaluationNotify: value),
                  ),
                ),
                const SizedBox(height: 6),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.circular(AppRadius.sm),
                  ),
                  child: const Text(
                    '开关状态保存在通知服务中，换设备登录后仍然生效。',
                    style: TextStyle(fontSize: 12, color: AppColors.muted),
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildSwitchRow({
    required String title,
    required String description,
    required bool value,
    required bool saving,
    required ValueChanged<bool> onChanged,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 14),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.fg,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  description,
                  style: const TextStyle(fontSize: 12, color: AppColors.muted),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Opacity(
            opacity: saving ? 0.5 : 1,
            child: AppSwitch(value: value, onChanged: saving ? null : onChanged),
          ),
        ],
      ),
    );
  }

  void _onNavSelect(String id) {
    switch (id) {
      case 'settings':
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
      case 'home':
        Navigator.pushNamedAndRemoveUntil(
          context,
          '/home',
          (route) => false,
        );
    }
  }
}
