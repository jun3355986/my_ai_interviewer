import 'dart:convert';
import 'package:dio/dio.dart';
import 'api_client.dart';
import '../models/evaluation_report.dart';
import '../models/interview_history.dart';
import '../models/practice_stats.dart';

class InterviewApi {
  final ApiClient _apiClient;

  InterviewApi(this._apiClient);

  Future<InterviewLineagePage> getLineages({
    int current = 1,
    int size = 10,
    String? keyword,
    String sortBy = 'time',
    String status = 'all',
  }) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .get(
          ApiClient.interviewPath('/interviews/lineages'),
          queryParameters: {
            'current': current,
            'size': size,
            if (keyword != null && keyword.trim().isNotEmpty)
              'keyword': keyword.trim(),
            'sortBy': sortBy,
            'status': status,
          },
        );
    final data = _readSuccessData(response);
    if (data is! Map) {
      throw StateError('面试历史响应格式错误');
    }
    return InterviewLineagePage.fromJson(Map<String, dynamic>.from(data));
  }

  Future<LineageTree> getLineageTree(String lineageId) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .get(ApiClient.interviewPath('/interviews/lineages/$lineageId/tree'));
    return LineageTree.fromJson(_readMap(response, '面试分支树响应格式错误'));
  }

  Future<BranchTranscript> getBranchTranscript(String branchId) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .get(
          ApiClient.interviewPath('/interviews/branches/$branchId/transcript'),
        );
    final data = _readSuccessData(response);
    if (data is! Map) {
      throw StateError('面试回放响应格式错误');
    }
    return BranchTranscript.fromJson(Map<String, dynamic>.from(data));
  }

  Future<StartAttempt> startAttempt({
    required String turnId,
    int? resumeId,
    int? jobId,
  }) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .post(
          ApiClient.interviewPath('/interviews/start-attempts'),
          data: {'turnId': turnId, 'resumeId': ?resumeId, 'jobId': ?jobId},
        );
    return StartAttempt.fromJson(_readMap(response, '启动面试响应格式错误'));
  }

  /// 个人练习统计（总次数/进行中/最近活动/近 14 天趋势）。
  Future<PracticeStats> getMyStats() async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .get(ApiClient.interviewPath('/interviews/my/stats'));
    return PracticeStats.fromJson(
      _readMap(response, '练习统计响应格式错误'),
    );
  }

  /// 个人评估统计（平均分/分数分布等）。
  Future<EvaluationStatistics> getEvaluationStatistics() async {
    final response = await _apiClient
        .getServiceDio(ApiClient.evaluationBaseUrl)
        .get(ApiClient.evaluationPath('/evaluations/statistics'));
    return EvaluationStatistics.fromJson(
      _readMap(response, '评估统计响应格式错误'),
    );
  }

  /// 读取持久化评估报告；尚未生成时返回 null。
  Future<EvaluationReport?> getEvaluationReport(String branchId) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.evaluationBaseUrl)
        .get(ApiClient.evaluationPath('/evaluations/$branchId'));
    final body = response.data;
    if (response.statusCode != 200 || body is! Map || body['code'] != 200) {
      final message = body is Map ? body['message']?.toString() : null;
      throw StateError(message ?? '请求失败');
    }
    final data = body['data'];
    if (data is! Map) return null;
    return EvaluationReport.fromJson(Map<String, dynamic>.from(data));
  }

  Future<EvaluationReport> generateEvaluationReport(String branchId) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.evaluationBaseUrl)
        .post(ApiClient.evaluationPath('/evaluations/$branchId'));
    return EvaluationReport.fromJson(_readMap(response, '评估报告响应格式错误'));
  }

  Future<TurnAttempt> createTurnAttempt({
    required String branchId,
    required String turnId,
    required String candidateAnswer,
    required int expectedBranchVersion,
    required int? expectedTailMessageId,
  }) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .post(
          ApiClient.interviewPath(
            '/interviews/branches/$branchId/turn-attempts',
          ),
          data: {
            'turnId': turnId,
            'candidateAnswer': candidateAnswer,
            'expectedBranchVersion': expectedBranchVersion,
            'expectedTailMessageId': expectedTailMessageId,
          },
        );
    return TurnAttempt.fromJson(_readMap(response, 'Turn Attempt响应格式错误'));
  }

  Future<ForkAttempt> createForkAttempt({
    required String focusedBranchId,
    required String turnId,
    required int triggerMessageId,
    required String candidateAnswer,
    required int expectedFocusedBranchVersion,
    required int? expectedFocusedTailMessageId,
  }) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .post(
          ApiClient.interviewPath(
            '/interviews/branches/$focusedBranchId/fork-attempts',
          ),
          data: {
            'turnId': turnId,
            'triggerMessageId': triggerMessageId,
            'candidateAnswer': candidateAnswer,
            'expectedFocusedBranchVersion': expectedFocusedBranchVersion,
            'expectedFocusedTailMessageId': expectedFocusedTailMessageId,
          },
        );
    return ForkAttempt.fromJson(_readMap(response, '分支创建响应格式错误'));
  }

  Future<TurnAttempt> getTurnAttempt(String turnId) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .get(ApiClient.interviewPath('/interviews/turn-attempts/$turnId'));
    return TurnAttempt.fromJson(_readMap(response, 'Turn Attempt响应格式错误'));
  }

  Stream<TurnAttemptEvent> getTurnAttemptEvents(String turnId) async* {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .get(
          ApiClient.interviewPath('/interviews/turn-attempts/$turnId/events'),
          options: Options(responseType: ResponseType.stream),
        );
    final lines = response.data.stream
        .cast<List<int>>()
        .transform(utf8.decoder)
        .transform(const LineSplitter());
    String? eventType;
    String? data;
    await for (final line in lines) {
      if (line.isEmpty) {
        if (data != null) {
          final decoded = jsonDecode(data);
          if (decoded is Map) {
            final payload = Map<String, dynamic>.from(decoded);
            payload.putIfAbsent('type', () => eventType ?? 'message');
            yield TurnAttemptEvent.fromJson(payload);
          }
        }
        eventType = null;
        data = null;
      } else if (line.startsWith('event:')) {
        eventType = line.substring(6).trim();
      } else if (line.startsWith('data:')) {
        data = line.substring(5).trim();
      }
    }
  }

  Future<TurnAttempt> retryTurnAttempt({
    required String originalTurnId,
    required String turnId,
    required String candidateAnswer,
    required int expectedBranchVersion,
    required int? expectedTailMessageId,
  }) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .post(
          ApiClient.interviewPath(
            '/interviews/turn-attempts/$originalTurnId/retry',
          ),
          data: {
            'turnId': turnId,
            'candidateAnswer': candidateAnswer,
            'expectedBranchVersion': expectedBranchVersion,
            'expectedTailMessageId': expectedTailMessageId,
          },
        );
    return TurnAttempt.fromJson(_readMap(response, '重试响应格式错误'));
  }

  Future<TurnAttempt> cancelTurnAttempt(String turnId) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .post(
          ApiClient.interviewPath('/interviews/turn-attempts/$turnId/cancel'),
        );
    return TurnAttempt.fromJson(_readMap(response, '取消响应格式错误'));
  }

  Future<TurnAttempt> discardTurnAttempt(String turnId) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.interviewBaseUrl)
        .post(
          ApiClient.interviewPath('/interviews/turn-attempts/$turnId/discard'),
        );
    return TurnAttempt.fromJson(_readMap(response, '丢弃响应格式错误'));
  }

  Map<String, dynamic> _readMap(Response response, String errorMessage) {
    final data = _readSuccessData(response);
    if (data is! Map) {
      throw StateError(errorMessage);
    }
    return Map<String, dynamic>.from(data);
  }

  dynamic _readSuccessData(Response response) {
    final body = response.data;
    if (response.statusCode != 200 || body is! Map || body['code'] != 200) {
      final message = body is Map ? body['message']?.toString() : null;
      throw StateError(message ?? '请求失败');
    }
    return body['data'];
  }

  /// Start or continue chat via SSE
  Future<void> chat({
    required String? sessionId,
    required String message,
    required String? resumeId,
    required String? jobId,
    required Function(String event, Map<String, dynamic> data) onEvent,
    required Function(dynamic error) onError,
    required Function() onDone,
  }) async {
    try {
      final response = await _apiClient
          .getServiceDio(ApiClient.interviewBaseUrl)
          .post(
            ApiClient.interviewPath('/interviews/chat'),
            data: {
              'sessionId': sessionId,
              'message': message,
              'resumeId': resumeId,
              'jobId': jobId,
            },
            options: Options(
              responseType: ResponseType.stream,
              headers: {
                'Accept': 'text/event-stream',
                'Cache-Control': 'no-cache',
                'Connection': 'keep-alive',
              },
            ),
          );

      final stream = response.data.stream.cast<List<int>>();
      String currentEvent = '';
      String currentData = '';

      stream
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen(
            (String line) {
              if (line.isEmpty) {
                if (currentEvent.isNotEmpty && currentData.isNotEmpty) {
                  try {
                    final data = jsonDecode(currentData);
                    onEvent(currentEvent, data);
                  } catch (e) {
                    // print('Error parsing SSE data: $e');
                  }
                }
                currentEvent = '';
                currentData = '';
              } else if (line.startsWith('event:')) {
                currentEvent = line.substring(6).trim();
              } else if (line.startsWith('data:')) {
                currentData = line.substring(5).trim();
              }
            },
            onError: (e) {
              onError(e);
            },
            onDone: () {
              onDone();
            },
          );
    } catch (e) {
      onError(e);
    }
  }
}
