import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:ai_interviewer_front/api/api_client.dart';
import 'package:ai_interviewer_front/services/pending_start_store.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'accessToken': 'expired-access-token',
      'refreshToken': 'expired-refresh-token',
      SharedPreferencesPendingStartStore.storageKey:
          '{"turnId":"account-a-start","resumeId":null,"jobId":10}',
    });
  });

  test(
    'protected 401 with a failed refresh clears credentials and expires the session',
    () async {
      var sessionExpiredCalls = 0;
      final client = ApiClient(
        dio: _dioRespondingWith((_) => _jsonResponse(401, {'code': 2000})),
        createRefreshDio: () =>
            _dioRespondingWith((_) => _jsonResponse(401, {'code': 2000})),
        onSessionExpired: () async {
          sessionExpiredCalls++;
        },
      );

      await expectLater(
        client.dio.get('/resumes'),
        throwsA(isA<DioException>()),
      );

      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('accessToken'), isNull);
      expect(prefs.getString('refreshToken'), isNull);
      expect(
        prefs.getString(SharedPreferencesPendingStartStore.storageKey),
        isNull,
      );
      expect(sessionExpiredCalls, 1);
    },
  );

  test(
    'a login 401 neither clears credentials nor triggers session expiry',
    () async {
      var sessionExpiredCalls = 0;
      final client = ApiClient(
        dio: _dioRespondingWith((_) => _jsonResponse(401, {'code': 1002})),
        onSessionExpired: () async {
          sessionExpiredCalls++;
        },
      );

      await expectLater(
        client.dio.post('/api/v1/auth/login'),
        throwsA(isA<DioException>()),
      );

      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('accessToken'), 'expired-access-token');
      expect(prefs.getString('refreshToken'), 'expired-refresh-token');
      expect(sessionExpiredCalls, 0);
    },
  );
}

Dio _dioRespondingWith(
  ResponseBody Function(RequestOptions options) responder,
) {
  final dio = Dio(BaseOptions(baseUrl: 'http://api.example.test'));
  dio.httpClientAdapter = _FakeAdapter(responder);
  return dio;
}

ResponseBody _jsonResponse(int statusCode, Map<String, dynamic> payload) {
  return ResponseBody.fromString(
    jsonEncode(payload),
    statusCode,
    headers: const {
      'content-type': ['application/json'],
    },
  );
}

class _FakeAdapter implements HttpClientAdapter {
  _FakeAdapter(this._responder);

  final ResponseBody Function(RequestOptions options) _responder;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    return _responder(options);
  }

  @override
  void close({bool force = false}) {}
}
