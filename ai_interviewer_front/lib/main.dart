import 'package:flutter/material.dart';
import 'login_page.dart';
import 'home_page.dart';
import 'upload_resume_page.dart';
import 'interview_chat_page.dart';
import 'interview_result_page.dart';
import 'interview_history_page.dart';
import 'history_detail_page.dart';
import 'api/api_client.dart';
import 'api/user_api.dart';
import 'api/job_api.dart';
import 'api/resume_api.dart';
import 'api/interview_api.dart';
import 'services/auth_service.dart';
import 'services/job_service.dart';
import 'services/resume_service.dart';
import 'services/interview_service.dart';
import 'package:provider/provider.dart';

void main() {
  final apiClient = ApiClient();
  final authApi = AuthApi(apiClient);
  final userApi = UserApi(apiClient);
  final jobApi = JobApi(apiClient);
  final resumeApi = ResumeApi(apiClient);
  final interviewApi = InterviewApi(apiClient);

  final authService = AuthService(authApi, userApi);
  final jobService = JobService(jobApi);
  final resumeService = ResumeService(resumeApi);
  final interviewService = InterviewService(interviewApi);

  runApp(
    MultiProvider(
      providers: [
        Provider<AuthService>.value(value: authService),
        Provider<JobService>.value(value: jobService),
        ChangeNotifierProvider<ResumeService>.value(value: resumeService),
        ChangeNotifierProvider<InterviewService>.value(value: interviewService),
      ],
      child: const AIInterviewerApp(),
    ),
  );
}

class AIInterviewerApp extends StatelessWidget {
  const AIInterviewerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AI 面试官助手',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primaryColor: const Color(0xFF2B7FFF),
        scaffoldBackgroundColor: const Color(0xFFF9FAFB),
        fontFamily: 'PingFang SC',
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF2B7FFF),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      ),
      initialRoute: '/login',
      routes: {
        '/login': (context) => const LoginPage(),
        '/home': (context) => const HomePage(),
        '/upload': (context) => const UploadResumePage(),
        '/chat': (context) => const InterviewChatPage(),
        '/result': (context) => const InterviewResultPage(),
        '/history': (context) => const InterviewHistoryPage(),
        '/history-detail': (context) => const HistoryDetailPage(),
      },
    );
  }
}
