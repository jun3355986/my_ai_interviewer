package com.aiinterviewer.admin.resume.mapper;

import com.aiinterviewer.admin.resume.AdminResumeService;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface AdminResumeMapper {

    Long countResumes(@Param("query") AdminResumeService.AdminResumeQuery query);

    List<AdminResumeService.AdminResumeListItem> selectResumes(
            @Param("query") AdminResumeService.AdminResumeQuery query,
            @Param("limit") long limit,
            @Param("offset") long offset);

    AdminResumeService.AdminResumeDetail selectResumeDetail(@Param("resumeId") Long resumeId);

    List<AdminResumeService.AdminResumeVersionItem> selectResumeVersions(@Param("resumeId") Long resumeId);
}
