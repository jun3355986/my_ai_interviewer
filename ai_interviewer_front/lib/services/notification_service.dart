import 'package:flutter/foundation.dart';

import '../api/notification_api.dart';
import '../models/notification_item.dart';

/// 通知状态：列表、未读红点、偏好设置，全部走真实通知服务。
class NotificationService extends ChangeNotifier {
  NotificationService(this._api);

  final NotificationApi _api;

  List<NotificationItem> _items = const [];
  List<NotificationItem> get items => _items;

  int _unreadCount = 0;
  int get unreadCount => _unreadCount;

  bool _isLoading = false;
  bool get isLoading => _isLoading;

  String? _error;
  String? get error => _error;

  NotificationPreference? _preference;
  NotificationPreference? get preference => _preference;

  bool _savingPreference = false;
  bool get savingPreference => _savingPreference;

  Future<void> loadAll() async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    try {
      final results = await Future.wait([
        _api.listNotifications(),
        _api.getUnreadCount(),
      ]);
      _items = results[0] as List<NotificationItem>;
      _unreadCount = results[1] as int;
    } catch (e) {
      _error = e.toString().replaceFirst('Bad state: ', '');
      debugPrint('NotificationService.loadAll error: $e');
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> refreshUnreadCount() async {
    try {
      _unreadCount = await _api.getUnreadCount();
      notifyListeners();
    } catch (e) {
      debugPrint('NotificationService.refreshUnreadCount error: $e');
    }
  }

  Future<void> markAsRead(int notificationId) async {
    try {
      await _api.markAsRead(notificationId);
      _items = _items
          .map(
            (item) => item.id == notificationId
                ? NotificationItem(
                    id: item.id,
                    userId: item.userId,
                    type: item.type,
                    typeText: item.typeText,
                    title: item.title,
                    content: item.content,
                    relatedType: item.relatedType,
                    relatedId: item.relatedId,
                    status: item.status,
                    statusText: item.statusText,
                    isRead: true,
                    createdAt: item.createdAt,
                  )
                : item,
          )
          .toList();
      await refreshUnreadCount();
      notifyListeners();
    } catch (e) {
      debugPrint('NotificationService.markAsRead error: $e');
      rethrow;
    }
  }

  Future<void> markAllAsRead() async {
    try {
      await _api.markAllAsRead();
      _items = _items
          .map(
            (item) => NotificationItem(
              id: item.id,
              userId: item.userId,
              type: item.type,
              typeText: item.typeText,
              title: item.title,
              content: item.content,
              relatedType: item.relatedType,
              relatedId: item.relatedId,
              status: item.status,
              statusText: item.statusText,
              isRead: true,
              createdAt: item.createdAt,
            ),
          )
          .toList();
      _unreadCount = 0;
      notifyListeners();
    } catch (e) {
      debugPrint('NotificationService.markAllAsRead error: $e');
      rethrow;
    }
  }

  Future<void> loadPreference() async {
    try {
      _preference = await _api.getPreferences();
      notifyListeners();
    } catch (e) {
      debugPrint('NotificationService.loadPreference error: $e');
      rethrow;
    }
  }

  Future<void> savePreference(NotificationPreference preference) async {
    _savingPreference = true;
    notifyListeners();
    try {
      _preference = await _api.updatePreferences(preference);
    } finally {
      _savingPreference = false;
      notifyListeners();
    }
  }
}
