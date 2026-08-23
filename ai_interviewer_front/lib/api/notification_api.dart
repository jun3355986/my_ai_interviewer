import 'package:dio/dio.dart';

import '../models/notification_item.dart';
import 'api_client.dart';

/// 通知服务 API：列表、未读数、已读、通知偏好。
class NotificationApi {
  NotificationApi(this._apiClient);

  final ApiClient _apiClient;

  Future<List<NotificationItem>> listNotifications() async {
    final response = await _apiClient
        .getServiceDio(ApiClient.notificationBaseUrl)
        .get(ApiClient.notificationPath('/notifications'));
    final body = _readBody(response);
    final data = body['data'];
    if (data is! List) {
      throw StateError('通知列表响应格式错误');
    }
    return data
        .whereType<Map>()
        .map(
          (item) => NotificationItem.fromJson(Map<String, dynamic>.from(item)),
        )
        .toList();
  }

  Future<int> getUnreadCount() async {
    final response = await _apiClient
        .getServiceDio(ApiClient.notificationBaseUrl)
        .get(ApiClient.notificationPath('/notifications/unread-count'));
    final body = _readBody(response);
    final data = body['data'];
    if (data is num) return data.toInt();
    return int.tryParse('$data') ?? 0;
  }

  Future<void> markAsRead(int notificationId) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.notificationBaseUrl)
        .put(ApiClient.notificationPath('/notifications/$notificationId/read'));
    _readBody(response);
  }

  Future<void> markAllAsRead() async {
    final response = await _apiClient
        .getServiceDio(ApiClient.notificationBaseUrl)
        .put(ApiClient.notificationPath('/notifications/read-all'));
    _readBody(response);
  }

  Future<NotificationPreference> getPreferences() async {
    final response = await _apiClient
        .getServiceDio(ApiClient.notificationBaseUrl)
        .get(ApiClient.notificationPath('/notifications/preferences'));
    final body = _readBody(response);
    final data = body['data'];
    if (data is! Map) {
      throw StateError('通知偏好响应格式错误');
    }
    return NotificationPreference.fromJson(Map<String, dynamic>.from(data));
  }

  Future<NotificationPreference> updatePreferences(
    NotificationPreference preference,
  ) async {
    final response = await _apiClient
        .getServiceDio(ApiClient.notificationBaseUrl)
        .put(
          ApiClient.notificationPath('/notifications/preferences'),
          data: preference.toJson(),
        );
    final body = _readBody(response);
    final data = body['data'];
    if (data is! Map) {
      throw StateError('通知偏好更新响应格式错误');
    }
    return NotificationPreference.fromJson(Map<String, dynamic>.from(data));
  }

  Map<String, dynamic> _readBody(Response response) {
    final body = response.data;
    if (response.statusCode != 200 || body is! Map || body['code'] != 200) {
      final message = body is Map ? body['message']?.toString() : null;
      throw StateError(message ?? '请求失败');
    }
    return Map<String, dynamic>.from(body);
  }
}
