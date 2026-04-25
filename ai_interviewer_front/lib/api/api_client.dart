import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

  late Dio dio;

  ApiClient() {
    dio = Dio();
    dio.interceptors.add(
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
          if (e.response?.statusCode == 401) {
            if (e.requestOptions.extra['__retried'] == true) {
              return handler.next(e);
            }

            // Implement refresh token logic
            final prefs = await SharedPreferences.getInstance();
            final refreshToken = prefs.getString('refreshToken');
            
            if (refreshToken != null) {
              try {
                // Create a new Dio instance to avoid interceptor loops
                final refreshDio = Dio();
                // Use the same base URL as the failing request, or force gateway/auth URL
                // Here we use userBaseUrl because auth is there
                final refreshUrl = '${_resolveBaseUrl(userBaseUrl)}${authPath('/auth/refresh')}';
                
                final response = await refreshDio.post(
                  refreshUrl,
                  data: {'refreshToken': refreshToken},
                );

                if (response.statusCode == 200 && response.data['code'] == 200) {
                  final newAccessToken = response.data['data']['accessToken'];
                  final newRefreshToken = response.data['data']['refreshToken']; // If backend rotates it

                  await prefs.setString('accessToken', newAccessToken);
                  if (newRefreshToken != null) {
                    await prefs.setString('refreshToken', newRefreshToken);
                  }

                  // Retry original request with new token
                  final options = e.requestOptions;
                  options.headers['Authorization'] = 'Bearer $newAccessToken';
                  options.extra['__retried'] = true;
                  
                  // Use dio.fetch to respect the original baseUrl in options and avoid race conditions
                  final cloneReq = await dio.fetch(options);
                  
                  return handler.resolve(cloneReq);
                }
              } catch (refreshError) {
                // Refresh failed, maybe logout
                // For now just pass the error through
              }
            }
          }
          return handler.next(e);
        },
      ),
    );
    dio.interceptors.add(LogInterceptor(responseBody: true, requestBody: true));
  }

  String _resolveBaseUrl(String baseUrl) {
    final trimmed = baseUrl.trim();

    // Relative root ("/") would turn "/api/..." into protocol-relative "//api/..."
    // in some browser request builders, so normalize it to current origin.
    if (trimmed.isEmpty || trimmed == '/') {
      return Uri.base.origin;
    }

    if (trimmed.startsWith('/')) {
      final normalizedPath = trimmed == '/' ? '' : trimmed.replaceAll(RegExp(r'/+$'), '');
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
