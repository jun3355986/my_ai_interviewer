import 'dart:convert';
import 'package:dio/dio.dart';
import 'api_client.dart';

class InterviewApi {
  final ApiClient _apiClient;

  InterviewApi(this._apiClient);

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
      final response = await _apiClient.getServiceDio(ApiClient.interviewBaseUrl).post(
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
