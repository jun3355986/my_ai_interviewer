import 'package:dio/dio.dart';
import 'api_client.dart';

class AuthApi {
  final ApiClient _apiClient;

  AuthApi(this._apiClient);

  Future<Response> login(String username, String password) {
    return _apiClient
        .getServiceDio(ApiClient.userBaseUrl)
        .post(
          ApiClient.authPath('/auth/login'),
          data: {'username': username, 'password': password},
        );
  }

  Future<Response> register(Map<String, dynamic> data) {
    return _apiClient
        .getServiceDio(ApiClient.userBaseUrl)
        .post(ApiClient.authPath('/auth/register'), data: data);
  }

  Future<Response> logout() {
    return _apiClient
        .getServiceDio(ApiClient.userBaseUrl)
        .post(ApiClient.authPath('/auth/logout'));
  }

  Future<Response> refresh(String refreshToken) {
    return _apiClient
        .getServiceDio(ApiClient.userBaseUrl)
        .post(
          ApiClient.authPath('/auth/refresh'),
          data: {'refreshToken': refreshToken},
        );
  }
}

class UserApi {
  final ApiClient _apiClient;

  UserApi(this._apiClient);

  Future<Response> getMe() {
    return _apiClient
        .getServiceDio(ApiClient.userBaseUrl)
        .get(ApiClient.userPath('/users/me'));
  }

  Future<Response> updateMe(Map<String, dynamic> data) {
    return _apiClient
        .getServiceDio(ApiClient.userBaseUrl)
        .put(ApiClient.userPath('/users/me'), data: data);
  }

  Future<Response> getUserById(String id) {
    return _apiClient
        .getServiceDio(ApiClient.userBaseUrl)
        .get(ApiClient.userPath('/users/$id'));
  }
}
