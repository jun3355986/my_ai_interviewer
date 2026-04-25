import 'package:dio/dio.dart';
import 'api_client.dart';

class JobApi {
  final ApiClient _apiClient;

  JobApi(this._apiClient);

  Future<Response> getJobs() {
    return _apiClient
        .getServiceDio(ApiClient.jobBaseUrl)
        .get(ApiClient.jobPath('/jobs'));
  }

  Future<Response> searchJobs(String keyword) {
    return _apiClient
        .getServiceDio(ApiClient.jobBaseUrl)
        .get(
          ApiClient.jobPath('/jobs/search'),
          queryParameters: {'keyword': keyword},
        );
  }

  Future<Response> createJob(Map<String, dynamic> data) {
    return _apiClient
        .getServiceDio(ApiClient.jobBaseUrl)
        .post(ApiClient.jobPath('/jobs'), data: data);
  }

  Future<Response> getJobById(String id) {
    return _apiClient
        .getServiceDio(ApiClient.jobBaseUrl)
        .get(ApiClient.jobPath('/jobs/$id'));
  }

  Future<Response> updateJob(String id, Map<String, dynamic> data) {
    return _apiClient
        .getServiceDio(ApiClient.jobBaseUrl)
        .put(ApiClient.jobPath('/jobs/$id'), data: data);
  }

  Future<Response> deleteJob(String id) {
    return _apiClient
        .getServiceDio(ApiClient.jobBaseUrl)
        .delete(ApiClient.jobPath('/jobs/$id'));
  }

  Future<Response> closeJob(String id) {
    return _apiClient
        .getServiceDio(ApiClient.jobBaseUrl)
        .put(ApiClient.jobPath('/jobs/$id/close'));
  }

  Future<Response> matchJob(
    String jobId,
    String resumeId,
    String resumeContent,
  ) {
    return _apiClient
        .getServiceDio(ApiClient.jobBaseUrl)
        .post(
          ApiClient.jobPath('/jobs/$jobId/match'),
          data: {'resumeId': resumeId, 'resumeContent': resumeContent},
        );
  }

  Future<Response> getMyJobs() {
    return _apiClient
        .getServiceDio(ApiClient.jobBaseUrl)
        .get(ApiClient.jobPath('/jobs/my'));
  }
}
