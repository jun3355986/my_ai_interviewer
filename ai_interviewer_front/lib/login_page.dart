import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'design/app_design.dart';
import 'services/auth_service.dart';

/// 登录 / 注册页：登录卡切换注册，全部走真实用户服务。
class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _registerUsernameController =
      TextEditingController();
  final TextEditingController _registerPasswordController =
      TextEditingController();
  final TextEditingController _registerNicknameController =
      TextEditingController();

  bool _obscurePassword = true;
  bool _isRegisterMode = false;
  bool _submitting = false;
  String? _formError;

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    _registerUsernameController.dispose();
    _registerPasswordController.dispose();
    _registerNicknameController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    final username = _usernameController.text.trim();
    final password = _passwordController.text;
    if (username.isEmpty || password.length < 6) {
      setState(() => _formError = '请输入用户名，且密码至少 6 位。');
      return;
    }
    await _submit(() async {
      final result = await context.read<AuthService>().login(
        username,
        password,
      );
      if (result == null) {
        setState(() => _formError = '登录失败，请检查用户名或密码');
        return;
      }
      if (!mounted) return;
      Navigator.pushReplacementNamed(context, '/home');
    });
  }

  Future<void> _handleRegister() async {
    final username = _registerUsernameController.text.trim();
    final password = _registerPasswordController.text;
    final nickname = _registerNicknameController.text.trim();
    if (username.isEmpty || password.length < 6) {
      setState(() => _formError = '请填写用户名和至少 6 位密码。');
      return;
    }
    await _submit(() async {
      final success = await context.read<AuthService>().register(
        username,
        password,
        '$username@example.com',
        nickname.isEmpty ? username : nickname,
      );
      if (!mounted) return;
      if (success) {
        setState(() {
          _isRegisterMode = false;
          _formError = null;
          _usernameController.text = username;
        });
        if (mounted) showAppToast(context, '注册成功，请重新登录');
      } else {
        setState(() => _formError = '注册失败，用户名或邮箱可能已被使用');
      }
    });
  }

  Future<void> _submit(Future<void> Function() action) async {
    setState(() {
      _submitting = true;
      _formError = null;
    });
    try {
      await action();
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: _buildLoginCard(),
          ),
        ),
      ),
    );
  }

  Widget _buildLoginCard() {
    return Container(
      width: 440,
      padding: const EdgeInsets.all(32),
      decoration: BoxDecoration(
        color: AppColors.bg,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        border: Border.all(color: AppColors.borderSoft),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.08),
            blurRadius: 32,
            offset: const Offset(0, 12),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              const BrandMark(),
              const SizedBox(width: 12),
              const Text(
                '面试助手 · 练习',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 1,
                  color: AppColors.meta,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text(
            _isRegisterMode ? '创建练习账号' : '进入面试练习',
            style: const TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.w700,
              letterSpacing: -0.5,
              color: AppColors.fg,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            _isRegisterMode
                ? '注册后即可上传简历、开始模拟面试并回放历史分支。'
                : '登录后可以上传简历、开始模拟面试，并回放自己的历史分支。',
            style: const TextStyle(fontSize: 14, color: AppColors.muted, height: 1.5),
          ),
          const SizedBox(height: 22),
          AppSegmentedControl<bool>(
            segments: const [(false, '登录'), (true, '注册')],
            selected: _isRegisterMode,
            onChanged: (value) => setState(() {
              _isRegisterMode = value;
              _formError = null;
            }),
          ),
          const SizedBox(height: 22),
          if (_isRegisterMode) _buildRegisterForm() else _buildLoginForm(),
          const SizedBox(height: 20),
          _buildDivider(),
          const SizedBox(height: 20),
          _buildSocialButtons(),
        ],
      ),
    );
  }

  Widget _buildLoginForm() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const AppFieldLabel('用户名'),
        const SizedBox(height: 6),
        TextField(
          controller: _usernameController,
          autocorrect: false,
          textInputAction: TextInputAction.next,
          decoration: appInputDecoration(hint: '请输入用户名'),
        ),
        const SizedBox(height: 14),
        const AppFieldLabel('密码'),
        const SizedBox(height: 6),
        TextField(
          controller: _passwordController,
          obscureText: _obscurePassword,
          autocorrect: false,
          onSubmitted: (_) => _submitting ? null : _handleLogin(),
          decoration: appInputDecoration(
            hint: '至少 6 位密码',
            invalid: _formError != null,
            suffix: IconButton(
              tooltip: _obscurePassword ? '显示密码' : '隐藏密码',
              onPressed: () =>
                  setState(() => _obscurePassword = !_obscurePassword),
              icon: Icon(
                _obscurePassword ? Icons.visibility_off : Icons.visibility,
                size: 20,
                color: AppColors.meta,
              ),
            ),
          ),
        ),
        if (_formError != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              _formError!,
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppColors.danger,
              ),
            ),
          ),
        Align(
          alignment: Alignment.centerRight,
          child: Padding(
            padding: const EdgeInsets.only(top: 4),
            child: TextButton(
              onPressed: _showForgotDialog,
              style: TextButton.styleFrom(
                foregroundColor: AppColors.fg2,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                minimumSize: const Size(0, 36),
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
              child: const Text(
                '忘记密码?',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
              ),
            ),
          ),
        ),
        const SizedBox(height: 8),
        AppButton(
          label: _submitting ? '正在验证…' : '登录',
          loading: _submitting,
          onPressed: _submitting ? null : _handleLogin,
        ),
      ],
    );
  }

  Widget _buildRegisterForm() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const AppFieldLabel('用户名'),
        const SizedBox(height: 6),
        TextField(
          controller: _registerUsernameController,
          autocorrect: false,
          textInputAction: TextInputAction.next,
          decoration: appInputDecoration(hint: '用于登录的用户名'),
        ),
        const SizedBox(height: 14),
        const AppFieldLabel('昵称（可选）'),
        const SizedBox(height: 6),
        TextField(
          controller: _registerNicknameController,
          textInputAction: TextInputAction.next,
          decoration: appInputDecoration(hint: '展示在首页与设置页'),
        ),
        const SizedBox(height: 14),
        const AppFieldLabel('密码'),
        const SizedBox(height: 6),
        TextField(
          controller: _registerPasswordController,
          obscureText: _obscurePassword,
          onSubmitted: (_) => _submitting ? null : _handleRegister(),
          decoration: appInputDecoration(
            hint: '至少 6 位密码',
            invalid: _formError != null,
            suffix: IconButton(
              tooltip: _obscurePassword ? '显示密码' : '隐藏密码',
              onPressed: () =>
                  setState(() => _obscurePassword = !_obscurePassword),
              icon: Icon(
                _obscurePassword ? Icons.visibility_off : Icons.visibility,
                size: 20,
                color: AppColors.meta,
              ),
            ),
          ),
        ),
        if (_formError != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              _formError!,
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppColors.danger,
              ),
            ),
          ),
        const SizedBox(height: 10),
        const Text(
          '注册将使用「用户名@example.com」作为邮箱。',
          style: TextStyle(fontSize: 12, color: AppColors.meta),
        ),
        const SizedBox(height: 14),
        AppButton(
          label: _submitting ? '正在注册…' : '注册新账号',
          loading: _submitting,
          onPressed: _submitting ? null : _handleRegister,
        ),
      ],
    );
  }

  Widget _buildDivider() {
    return Row(
      children: [
        const Expanded(child: Divider(color: AppColors.border, height: 1)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14),
          child: Text(
            '或',
            style: TextStyle(fontSize: 12, color: AppColors.meta),
          ),
        ),
        const Expanded(child: Divider(color: AppColors.border, height: 1)),
      ],
    );
  }

  Widget _buildSocialButtons() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AppButton(
          label: '使用 Apple 登录',
          variant: AppButtonVariant.secondary,
          icon: Icons.apple,
          onPressed: () => showAppToast(context, 'Apple 登录尚未接入'),
        ),
        const SizedBox(height: 10),
        AppButton(
          label: '使用微信登录',
          variant: AppButtonVariant.secondary,
          icon: Icons.wechat,
          onPressed: () => showAppToast(context, '微信登录尚未接入'),
        ),
      ],
    );
  }

  void _showForgotDialog() {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.lg),
        ),
        title: const Text('忘记密码'),
        content: const Text(
          '当前版本尚未接入找回密码接口，请联系管理员重置密码。',
          style: TextStyle(color: AppColors.muted, height: 1.5),
        ),
        actions: [
          AppButton(
            label: '知道了',
            small: true,
            onPressed: () => Navigator.pop(dialogContext),
          ),
        ],
      ),
    );
  }
}
