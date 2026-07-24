import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/api/interview_api.dart';
import 'package:ai_interviewer_front/interview_chat_page.dart';
import 'package:ai_interviewer_front/models/interview_history.dart';
import 'package:ai_interviewer_front/services/interview_service.dart';

void main() {
  testWidgets('exiting an interview explains persistence and clears to home', (
    tester,
  ) async {
    await tester.pumpWidget(
      ChangeNotifierProvider(
        create: (_) => InterviewService(_NoopInterviewApi()),
        child: MaterialApp(
          initialRoute: '/chat',
          routes: {
            '/chat': (_) => const InterviewChatPage(),
            '/home': (_) => const Scaffold(body: Text('首页标记')),
          },
        ),
      ),
    );

    await tester.tap(find.byIcon(Icons.arrow_back_ios_new));
    await tester.pumpAndSettle();

    expect(find.textContaining('进度已保存'), findsOneWidget);

    await tester.tap(find.text('确定退出'));
    await tester.pumpAndSettle();

    expect(find.text('首页标记'), findsOneWidget);
    expect(find.byType(InterviewChatPage), findsNothing);
  });

  testWidgets(
    'exit copy does not promise an in-flight AI result is committed',
    (tester) async {
      final api = _StreamingInterviewApi();
      final service = InterviewService(api, reconnectDelays: const []);
      service.restoreAttempt(
        const TurnAttempt(
          turnId: 'processing-1',
          lineageId: 'lineage-1',
          branchId: 'root-1',
          expectedBranchVersion: 1,
          expectedTailMessageId: null,
          candidateAnswer: '我准备好了',
          status: 'PROCESSING',
        ),
      );

      await tester.pumpWidget(
        ChangeNotifierProvider.value(
          value: service,
          child: MaterialApp(
            initialRoute: '/chat',
            routes: {
              '/chat': (_) => const InterviewChatPage(),
              '/home': (_) => const Scaffold(body: Text('首页标记')),
            },
          ),
        ),
      );

      await tester.tap(find.byIcon(Icons.arrow_back_ios_new));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.textContaining('本轮结果尚未确认提交'), findsOneWidget);
      expect(find.textContaining('当前面试进度已保存'), findsNothing);
    },
  );

  testWidgets('top action exits without fabricating a completed report', (
    tester,
  ) async {
    await tester.pumpWidget(
      ChangeNotifierProvider(
        create: (_) => InterviewService(_NoopInterviewApi()),
        child: MaterialApp(
          initialRoute: '/chat',
          routes: {
            '/chat': (_) => const InterviewChatPage(),
            '/home': (_) => const Scaffold(body: Text('首页标记')),
            '/result': (_) => const Scaffold(body: Text('伪造报告标记')),
          },
        ),
      ),
    );

    await tester.tap(find.text('退出面试'));
    await tester.pump();
    expect(find.textContaining('进度已保存'), findsOneWidget);

    await tester.tap(find.text('确定退出'));
    await tester.pumpAndSettle();
    expect(find.text('首页标记'), findsOneWidget);
    expect(find.text('伪造报告标记'), findsNothing);
  });
}

class _NoopInterviewApi extends InterviewApi {
  _NoopInterviewApi() : super(ApiClient());
}

class _StreamingInterviewApi extends InterviewApi {
  _StreamingInterviewApi() : super(ApiClient());

  @override
  Stream<TurnAttemptEvent> getTurnAttemptEvents(String turnId) {
    return const Stream.empty();
  }

  @override
  Future<TurnAttempt> getTurnAttempt(String turnId) async {
    return TurnAttempt(
      turnId: turnId,
      lineageId: 'lineage-1',
      branchId: 'root-1',
      expectedBranchVersion: 1,
      expectedTailMessageId: null,
      candidateAnswer: '我准备好了',
      status: 'PROCESSING',
    );
  }
}
