import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import '../api/resume_api.dart';

class ResumeService extends ChangeNotifier {
  final ResumeApi _resumeApi;

  ResumeService(this._resumeApi);

  bool _isLoading = false;
  bool get isLoading => _isLoading;

  String? _error;
  String? get error => _error;

  /// Uploads a resume and returns the resumeId if successful
  Future<String?> uploadResume(PlatformFile file) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final response = await _resumeApi.uploadResume(file);
      if (response.statusCode == 200 && response.data['code'] == 200) {
        final data = response.data['data'];
        if (data is Map) {
          return data['id']?.toString();
        }
        return data?.toString();
      } else {
        _error = response.data['message'] ?? 'Upload failed';
      }
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
    return null;
  }

  /// 触发解析；成功返回 true，失败写入 [error]（不抛异常，由调用方决定是否容错）。
  Future<bool> parseResume(String resumeId) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final response = await _resumeApi.parseResume(resumeId);
      if (response.statusCode == 200 && response.data['code'] == 200) {
        return true;
      }
      _error = response.data['message'] ?? '解析失败';
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
    return false;
  }
}
