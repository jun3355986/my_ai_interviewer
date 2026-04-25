import '../models/job.dart';
import '../api/job_api.dart';
import 'package:flutter/foundation.dart';

class JobService {
  final JobApi _jobApi;

  JobService(this._jobApi);

  Future<List<Job>> getJobs() async {
    try {
      final response = await _jobApi.getJobs();
      if (response.statusCode == 200 && response.data['code'] == 200) {
        final List<dynamic> data = response.data['data'];
        return data.map((json) => Job.fromJson(json)).toList();
      }
    } catch (e) {
      debugPrint('GetJobs error: $e');
    }
    return [];
  }

  Future<List<Job>> searchJobs(String keyword) async {
    try {
      final response = await _jobApi.searchJobs(keyword);
      if (response.statusCode == 200 && response.data['code'] == 200) {
        final List<dynamic> data = response.data['data'];
        return data.map((json) => Job.fromJson(json)).toList();
      }
    } catch (e) {
      debugPrint('SearchJobs error: $e');
    }
    return [];
  }

  Future<MatchResult?> matchJob(
    String jobId,
    String resumeId,
    String resumeContent,
  ) async {
    try {
      final response = await _jobApi.matchJob(jobId, resumeId, resumeContent);
      if (response.statusCode == 200 && response.data['code'] == 200) {
        return MatchResult.fromJson(response.data['data']);
      }
    } catch (e) {
      debugPrint('MatchJob error: $e');
    }
    return null;
  }

  Future<Job?> getJobById(String id) async {
    try {
      final response = await _jobApi.getJobById(id);
      if (response.statusCode == 200 && response.data['code'] == 200) {
        return Job.fromJson(response.data['data']);
      }
    } catch (e) {
      debugPrint('GetJobById error: $e');
    }
    return null;
  }
}
