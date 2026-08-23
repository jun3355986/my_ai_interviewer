import '../models/user.dart';
import '../api/user_api.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/foundation.dart';

import 'pending_start_store.dart';

class AuthService {
  final AuthApi _authApi;
  final UserApi _userApi;

  AuthService(this._authApi, this._userApi);

  Future<LoginResponse?> login(String username, String password) async {
    try {
      final response = await _authApi.login(username, password);
      final payload = response.data as Map<String, dynamic>?;
      final rawCode = payload?['code'];
      final parsedCode = rawCode is int ? rawCode : int.tryParse('$rawCode');
      final success = payload?['success'] == true;
      final data = payload?['data'];

      if (response.statusCode == 200 &&
          (parsedCode == 200 || success) &&
          data is Map<String, dynamic>) {
        final loginData = LoginResponse.fromJson(data);
        if (loginData.accessToken.isEmpty || loginData.refreshToken.isEmpty) {
          return null;
        }
        await _saveTokens(loginData.accessToken, loginData.refreshToken);
        return loginData;
      }
    } catch (e) {
      debugPrint('Login error: $e');
    }
    return null;
  }

  Future<bool> register(
    String username,
    String password,
    String email,
    String nickname,
  ) async {
    try {
      final response = await _authApi.register({
        'username': username,
        'password': password,
        'confirmPassword': password,
        'email': email,
        'nickname': nickname,
      });
      return response.statusCode == 200 && response.data['code'] == 200;
    } catch (e) {
      debugPrint('Register error: $e');
      return false;
    }
  }

  Future<void> logout() async {
    try {
      await _authApi.logout();
    } finally {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove('accessToken');
      await prefs.remove('refreshToken');
      await const SharedPreferencesPendingStartStore().clearAll();
    }
  }

  Future<User?> getMe() async {
    try {
      final response = await _userApi.getMe();
      if (response.statusCode == 200 && response.data['code'] == 200) {
        return User.fromJson(response.data['data']);
      }
    } catch (e) {
      debugPrint('GetMe error: $e');
    }
    return null;
  }

  /// 更新个人资料（昵称/手机号/头像 URL），返回更新后的用户。
  Future<User?> updateMe({
    String? nickname,
    String? phone,
    String? avatarUrl,
  }) async {
    try {
      final response = await _userApi.updateMe({
        if (nickname != null) 'nickname': nickname,
        if (phone != null) 'phone': phone,
        'avatarUrl': ?avatarUrl,
      });
      if (response.statusCode == 200 && response.data['code'] == 200) {
        return User.fromJson(response.data['data']);
      }
    } catch (e) {
      debugPrint('UpdateMe error: $e');
    }
    return null;
  }

  Future<void> _saveTokens(String access, String refresh) async {
    await const SharedPreferencesPendingStartStore().clearAll();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('accessToken', access);
    await prefs.setString('refreshToken', refresh);
  }

  Future<bool> isLoggedIn() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString('accessToken') != null;
  }
}
