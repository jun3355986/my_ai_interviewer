package com.aiinterviewer.admin.interview.mapper;

import com.aiinterviewer.admin.interview.AdminInterviewService;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface AdminInterviewMapper {

    Long countInterviews(@Param("query") AdminInterviewService.AdminInterviewQuery query);

    List<AdminInterviewService.AdminInterviewListItem> selectInterviews(
            @Param("query") AdminInterviewService.AdminInterviewQuery query,
            @Param("limit") long limit,
            @Param("offset") long offset);

    AdminInterviewService.AdminInterviewDetail selectInterviewDetail(@Param("sessionId") String sessionId);

    AdminInterviewService.DiagnosisSessionSnapshot selectDiagnosisSnapshot(@Param("sessionId") String sessionId);

    List<AdminInterviewService.AdminInterviewMessageItem> selectMessages(@Param("sessionId") String sessionId);

    List<AdminInterviewService.AdminScoreRecordItem> selectScoreRecords(@Param("sessionId") String sessionId);

    AdminInterviewService.AdminEvaluationSummary selectEvaluationSummary(@Param("sessionId") String sessionId);

    Integer countExistingSession(@Param("sessionId") String sessionId);

    int cancelSession(@Param("sessionId") String sessionId);
}
