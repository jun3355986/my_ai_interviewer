package com.aiinterviewer.admin.evaluation.mapper;

import com.aiinterviewer.admin.evaluation.AdminEvaluationService;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface AdminEvaluationMapper {

    Long countEvaluations(@Param("query") AdminEvaluationService.AdminEvaluationQuery query);

    List<AdminEvaluationService.AdminEvaluationListItem> selectEvaluations(
            @Param("query") AdminEvaluationService.AdminEvaluationQuery query,
            @Param("limit") long limit,
            @Param("offset") long offset);

    AdminEvaluationService.AdminEvaluationListItem selectEvaluationById(@Param("id") Long id);

    AdminEvaluationService.AdminEvaluationListItem selectEvaluationBySessionId(@Param("sessionId") String sessionId);
}
