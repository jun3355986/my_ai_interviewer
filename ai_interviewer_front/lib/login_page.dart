import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'services/auth_service.dart';

/// AI 面试官助手 - 登录页面
/// 基于 Figma 设计实现
class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  bool _obscurePassword = true;

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFE8F0FE), // 浅蓝色背景
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Logo 图标
                _buildLogo(),
                const SizedBox(height: 24),

                // 标题
                const Text(
                  'AI 面试官助手',
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
                const SizedBox(height: 8),

                // 副标题
                const Text(
                  '让面试准备更智能',
                  style: TextStyle(fontSize: 16, color: Color(0xFF666666)),
                ),
                const SizedBox(height: 40),

                // 登录表单卡片
                _buildLoginCard(),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// Logo 图标
  Widget _buildLogo() {
    return Container(
      width: 80,
      height: 80,
      decoration: BoxDecoration(
        color: const Color(0xFF4285F4), // 蓝色
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF4285F4).withValues(alpha: 0.3),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: const Icon(
        Icons.chat_bubble_rounded,
        size: 40,
        color: Colors.white,
      ),
    );
  }

  /// 登录表单卡片
  Widget _buildLoginCard() {
    return Container(
      padding: const EdgeInsets.all(32),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 20,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 用户名输入框
          _buildInputLabel('用户名'),
          const SizedBox(height: 8),
          _buildUsernameField(),
          const SizedBox(height: 20),

          // 密码输入框
          _buildInputLabel('密码'),
          const SizedBox(height: 8),
          _buildPasswordField(),
          const SizedBox(height: 16),

          // 忘记密码
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(
              onPressed: () {
                // TODO: 实现忘记密码功能
              },
              child: const Text(
                '忘记密码?',
                style: TextStyle(color: Color(0xFF4285F4), fontSize: 14),
              ),
            ),
          ),
          const SizedBox(height: 8),

          // 登录按钮
          _buildLoginButton(),
          const SizedBox(height: 20),

          // 注册提示
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Text(
                '还没有账号? ',
                style: TextStyle(fontSize: 14, color: Color(0xFF666666)),
              ),
              TextButton(
                onPressed: () {
                  _handleRegister();
                },
                style: TextButton.styleFrom(
                  padding: EdgeInsets.zero,
                  minimumSize: const Size(0, 0),
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                ),
                child: const Text(
                  '注册新账号',
                  style: TextStyle(
                    fontSize: 14,
                    color: Color(0xFF4285F4),
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // 分隔线
          Row(
            children: [
              Expanded(
                child: Divider(
                  color: Colors.grey.withValues(alpha: 0.3),
                  thickness: 1,
                ),
              ),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 16),
                child: Text(
                  '或',
                  style: TextStyle(fontSize: 14, color: Color(0xFF999999)),
                ),
              ),
              Expanded(
                child: Divider(
                  color: Colors.grey.withValues(alpha: 0.3),
                  thickness: 1,
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // 第三方登录按钮
          _buildThirdPartyLogin(),
        ],
      ),
    );
  }

  /// 输入框标签
  Widget _buildInputLabel(String label) {
    return Text(
      label,
      style: const TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w500,
        color: Color(0xFF333333),
      ),
    );
  }

  /// 用户名输入框
  Widget _buildUsernameField() {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFFF5F5F5),
        borderRadius: BorderRadius.circular(12),
      ),
      child: TextField(
        controller: _usernameController,
        decoration: InputDecoration(
          hintText: '请输入用户名',
          hintStyle: TextStyle(
            color: Colors.grey.withValues(alpha: 0.6),
            fontSize: 14,
          ),
          prefixIcon: Icon(
            Icons.person_outline,
            color: Colors.grey.withValues(alpha: 0.6),
          ),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 14,
          ),
        ),
      ),
    );
  }

  /// 密码输入框
  Widget _buildPasswordField() {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFFF5F5F5),
        borderRadius: BorderRadius.circular(12),
      ),
      child: TextField(
        controller: _passwordController,
        obscureText: _obscurePassword,
        decoration: InputDecoration(
          hintText: '请输入密码',
          hintStyle: TextStyle(
            color: Colors.grey.withValues(alpha: 0.6),
            fontSize: 14,
          ),
          prefixIcon: Icon(
            Icons.lock_outline,
            color: Colors.grey.withValues(alpha: 0.6),
          ),
          suffixIcon: IconButton(
            icon: Icon(
              _obscurePassword ? Icons.visibility_off : Icons.visibility,
              color: Colors.grey.withValues(alpha: 0.6),
            ),
            onPressed: () {
              setState(() {
                _obscurePassword = !_obscurePassword;
              });
            },
          ),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 14,
          ),
        ),
      ),
    );
  }

  /// 登录按钮
  Widget _buildLoginButton() {
    return Container(
      height: 50,
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF4285F4), Color(0xFF5E9EFF)],
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
        ),
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF4285F4).withValues(alpha: 0.3),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () {
            // TODO: 实现登录逻辑
            _handleLogin();
          },
          borderRadius: BorderRadius.circular(12),
          child: const Center(
            child: Text(
              '登录',
              style: TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// 第三方登录
  Widget _buildThirdPartyLogin() {
    return Column(
      children: [
        // Apple 登录
        _buildThirdPartyButton(
          icon: Icons.apple,
          text: '使用 Apple 登录',
          iconColor: Colors.black,
          onTap: () {
            // TODO: 实现 Apple 登录
          },
        ),
        const SizedBox(height: 12),

        // 微信登录
        _buildThirdPartyButton(
          icon: Icons.wechat,
          text: '使用微信登录',
          iconColor: const Color(0xFF07C160),
          onTap: () {
            // TODO: 实现微信登录
          },
        ),
      ],
    );
  }

  /// 第三方登录按钮
  Widget _buildThirdPartyButton({
    required IconData icon,
    required String text,
    required Color iconColor,
    required VoidCallback onTap,
  }) {
    return Container(
      height: 50,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.withValues(alpha: 0.2), width: 1),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, color: iconColor, size: 24),
              const SizedBox(width: 12),
              Text(
                text,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: Color(0xFF333333),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 处理登录
  Future<void> _handleLogin() async {
    final username = _usernameController.text.trim();
    final password = _passwordController.text.trim();

    if (username.isEmpty || password.isEmpty) {
      _showMessage('请输入用户名和密码');
      return;
    }

    final authService = Provider.of<AuthService>(context, listen: false);

    // 显示加载状态
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(child: CircularProgressIndicator()),
    );

    final result = await authService.login(username, password);

    // 关闭加载框
    if (mounted) Navigator.pop(context);

    if (result != null) {
      if (mounted) Navigator.pushReplacementNamed(context, '/home');
    } else {
      _showMessage('登录失败，请检查用户名或密码');
    }
  }

  /// 处理注册 (简单演示，通常导航到专门的注册页)
  Future<void> _handleRegister() async {
    final username = _usernameController.text.trim();
    final password = _passwordController.text.trim();

    if (username.isEmpty || password.isEmpty) {
      _showMessage('请输入注册信息');
      return;
    }

    final authService = Provider.of<AuthService>(context, listen: false);

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(child: CircularProgressIndicator()),
    );

    final success = await authService.register(
      username,
      password,
      '$username@example.com',
      username,
    );

    if (mounted) Navigator.pop(context);

    if (success) {
      _showMessage('注册成功，请重新登录');
    } else {
      _showMessage('注册失败');
    }
  }

  /// 显示提示消息
  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), duration: const Duration(seconds: 2)),
    );
  }
}
