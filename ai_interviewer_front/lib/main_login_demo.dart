import 'package:flutter/material.dart';
import 'login_page.dart';

/// 登录页面演示入口
///
/// 使用方法：
/// 在 main.dart 中将 main() 函数修改为：
/// ```dart
/// void main() {
///   runApp(const LoginDemo());
/// }
/// ```
void main() {
  runApp(const LoginDemo());
}

class LoginDemo extends StatelessWidget {
  const LoginDemo({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AI 面试官助手 - 登录',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primarySwatch: Colors.blue,
        fontFamily: 'PingFang SC',
      ),
      home: const LoginPage(),
    );
  }
}
