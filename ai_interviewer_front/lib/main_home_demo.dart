import 'package:flutter/material.dart';
import 'home_page.dart';

/// 主页演示入口
///
/// 使用方法：
/// 在终端运行：flutter run -d chrome -t lib/main_home_demo.dart
void main() {
  runApp(const HomeDemo());
}

class HomeDemo extends StatelessWidget {
  const HomeDemo({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AI 面试官助手 - 主页',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primaryColor: const Color(0xFF4E7FF6),
        fontFamily: 'PingFang SC',
        scaffoldBackgroundColor: const Color(0xFFF5F5F7),
      ),
      home: const HomePage(),
    );
  }
}
