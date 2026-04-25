package com.aiinterviewer.job.mapper;

import com.aiinterviewer.job.entity.JobRequirement;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 职位要求Mapper
 */
@Mapper
public interface JobRequirementMapper extends BaseMapper<JobRequirement> {

    /**
     * 查询职位的要求列表
     */
    @Select("SELECT * FROM t_job_requirement WHERE job_id = #{jobId} ORDER BY weight DESC")
    List<JobRequirement> selectByJobId(@Param("jobId") Long jobId);
}
