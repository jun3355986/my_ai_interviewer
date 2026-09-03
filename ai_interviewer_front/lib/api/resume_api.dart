import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'api_client.dart';

class ResumeApi {
  final ApiClient _apiClient;

  ResumeApi(this._apiClient);

  /// Upload resume file
  /// Returns the response which should contain the resumeId
  Future<Response> uploadResume(PlatformFile file) async {
    final fileName = file.name;
    final multipartFile = file.bytes != null
        ? MultipartFile.fromBytes(file.bytes!, filename: fileName)
        : file.path != null
            ? await MultipartFile.fromFile(file.path!, filename: fileName)
            : throw StateError('No file content available for upload');

    FormData formData = FormData.fromMap({
      "file": multipartFile,
    });

    return _apiClient
        .getServiceDio(ApiClient.resumeBaseUrl)
        .post(ApiClient.resumePath('/resumes/upload'), data: formData);
  }

  /// 触发简历解析（Python 结构化解析 + 原文落库 raw_text）
  Future<Response> parseResume(String resumeId) {
    return _apiClient
        .getServiceDio(ApiClient.resumeBaseUrl)
        .post(
          ApiClient.resumePath('/resumes/$resumeId/parse'),
          data: <String, dynamic>{},
        );
  }
}
