package com.aiinterviewer.admin.job.mapper;

import com.aiinterviewer.admin.job.AdminJobService;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface AdminJobMapper {

    Long countJobs(@Param("query") AdminJobService.AdminJobQuery query);

    List<AdminJobService.AdminJobListItem> selectJobs(
            @Param("query") AdminJobService.AdminJobQuery query,
            @Param("limit") long limit,
            @Param("offset") long offset);

    AdminJobService.AdminJobDetail selectJobDetail(@Param("jobId") Long jobId);

    Integer countExistingJob(@Param("jobId") Long jobId);

    int insertJob(@Param("request") AdminJobService.AdminJobUpsertRequest request);

    int updateJob(@Param("request") AdminJobService.AdminJobUpsertRequest request);

    int updateJobStatus(
            @Param("jobId") Long jobId,
            @Param("status") Integer status);

    int deleteJobQuestions(@Param("jobId") Long jobId);

    int insertJobQuestions(
            @Param("jobId") Long jobId,
            @Param("questions") List<AdminJobService.JobQuestionConfigItem> questions);

    List<AdminJobService.JobQuestionConfigItem> selectJobQuestions(@Param("jobId") Long jobId);
}
