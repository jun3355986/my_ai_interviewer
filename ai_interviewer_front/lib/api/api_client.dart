import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../services/pending_start_store.dart';

typedef SessionExpiredCallback = Future<void> Function();

class ApiClient {
  static const String gatewayBaseUrl = String.fromEnvironment(
    'GATEWAY_BASE_URL',
    defaultValue: 'http://localhost:9000',
  );
  static const String _gatewayPrefix = '/api/v1';

  static String get userBaseUrl => gatewayBaseUrl;

  static String get jobBaseUrl => gatewayBaseUrl;

  static String get resumeBaseUrl => gatewayBaseUrl;

  static String get interviewBaseUrl => gatewayBaseUrl;

  static String authPath(String path) => _normalizePath(path);

  static String userPath(String path) => _normalizePath(path);

  static String jobPath(String path) => _normalizePath(path);

  static String resumePath(String path) => _normalizePath(path);

  static String interviewPath(String path) => _normalizePath(path);

  static String _normalizePath(String path) {
    final normalized = path.startsWith('/') ? path : '/$path';
    if (normalized.startsWith(_gatewayPrefix)) {
      return normalized;
    }

    return '$_gatewayPrefix$normalized';
  }

  ApiClient({Dio? dio, Dio Function()? createRefreshDio, this.onSessionExpired})
    : dio = dio ?? Dio(),
      _createRefreshDio = createRefreshDio ?? Dio.new {
    this.dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final prefs = await SharedPreferences.getInstance();
          final token = prefs.getString('accessToken');
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          return handler.next(options);
        },
        onError: (DioException e, handler) async {
          if (e.response?.statusCode != 401 ||
              _isPublicAuthRequest(e.requestOptions)) {
            return handler.next(e);
          }

          if (e.requestOptions.extra['__retried'] == true) {
            await _expireSession();
            return handler.next(e);
          }

          final prefs = await SharedPreferences.getInstance();
          final refreshToken = prefs.getString('refreshToken');
          if (refreshToken == null || refreshToken.isEmpty) {
            await _expireSession();
            return handler.next(e);
          }

          try {
            final refreshUrl =
                '${_resolveBaseUrl(userBaseUrl)}${authPath('/auth/refresh')}';
            final response = await _createRefreshDio().post(
              refreshUrl,
              data: {'refreshToken': refreshToken},
            );
            final refreshedTokens = _readRefreshedTokens(response);

            if (refreshedTokens == null) {
              await _expireSession();
              return handler.next(e);
            }

            await prefs.setString('accessToken', refreshedTokens.accessToken);
            if (refreshedTokens.refreshToken != null) {
              await prefs.setString(
                'refreshToken',
                refreshedTokens.refreshToken!,
              );
            }

            final options = e.requestOptions;
            options.headers['Authorization'] =
                'Bearer ${refreshedTokens.accessToken}';
            options.extra['__retried'] = true;

            final retriedResponse = await this.dio.fetch(options);
            return handler.resolve(retriedResponse);
          } catch (_) {
            await _expireSession();
            return handler.next(e);
          }
        },
      ),
    );
    this.dio.interceptors.add(
      LogInterceptor(responseBody: true, requestBody: true),
    );
  }

  final Dio dio;
  final Dio Function() _createRefreshDio;
  SessionExpiredCallback? onSessionExpired;
  Future<void>? _sessionExpiryInProgress;

  bool _isPublicAuthRequest(RequestOptions options) {
    return switch (options.uri.path) {
      '/api/v1/auth/login' ||
      '/api/v1/auth/register' ||
      '/api/v1/auth/refresh' => true,
      _ => false,
    };
  }

  _RefreshedTokens? _readRefreshedTokens(Response response) {
    if (response.statusCode != 200 || response.data is! Map) {
      return null;
    }

    final payload = response.data as Map;
    if (payload['code'] != 200 || payload['data'] is! Map) {
      return null;
    }

    final data = payload['data'] as Map;
    final accessToken = data['accessToken']?.toString();
    if (accessToken == null || accessToken.isEmpty) {
      return null;
    }

    final refreshToken = data['refreshToken']?.toString();
    return _RefreshedTokens(accessToken, refreshToken);
  }

  Future<void> _expireSession() {
    final activeExpiry = _sessionExpiryInProgress;
    if (activeExpiry != null) {
      return activeExpiry;
    }

    final expiry = _clearTokensAndNotifySessionExpired();
    _sessionExpiryInProgress = expiry;
    return expiry.whenComplete(() {
      if (identical(_sessionExpiryInProgress, expiry)) {
        _sessionExpiryInProgress = null;
      }
    });
  }

  Future<void> _clearTokensAndNotifySessionExpired() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('accessToken');
    await prefs.remove('refreshToken');
    await const SharedPreferencesPendingStartStore().clearAll();
    await onSessionExpired?.call();
  }

  String _resolveBaseUrl(String baseUrl) {
    final trimmed = baseUrl.trim();

    // Relative root ("/") would turn "/api/..." into protocol-relative "//api/..."
    // in some browser request builders, so normalize it to current origin.
    if (trimmed.isEmpty || trimmed == '/') {
      return Uri.base.origin;
    }

    if (trimmed.startsWith('/')) {
      final normalizedPath = trimmed == '/'
          ? ''
          : trimmed.replaceAll(RegExp(r'/+$'), '');
      return '${Uri.base.origin}$normalizedPath';
    }

    return trimmed.replaceAll(RegExp(r'/+$'), '');
  }

  // Helper to switch base URL or return a configured Dio instance
  Dio getServiceDio(String baseUrl) {
    dio.options.baseUrl = _resolveBaseUrl(baseUrl);
    return dio;
  }
}

class _RefreshedTokens {
  const _RefreshedTokens(this.accessToken, this.refreshToken);

  final String accessToken;
  final String? refreshToken;
}
