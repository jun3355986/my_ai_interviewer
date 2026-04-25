package com.aiinterviewer.resume.mapper;

import com.aiinterviewer.resume.entity.ResumeVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 简历版本历史Mapper接口
 */
@Mapper
public interface ResumeVersionMapper extends BaseMapper<ResumeVersion> {

    /**
     * 查询简历的所有版本
     */
    @Select("SELECT * FROM t_resume_version WHERE resume_id = #{resumeId} ORDER BY version DESC")
    List<ResumeVersion> selectByResumeId(@Param("resumeId") Long resumeId);

    /**
     * 获取简历最新版本号
     */
    @Select("SELECT COALESCE(MAX(version), 0) FROM t_resume_version WHERE resume_id = #{resumeId}")
    int selectMaxVersion(@Param("resumeId") Long resumeId);
}
