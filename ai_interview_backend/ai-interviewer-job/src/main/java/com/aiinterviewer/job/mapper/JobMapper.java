package com.aiinterviewer.job.mapper;

import com.aiinterviewer.job.entity.Job;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 职位Mapper
 */
@Mapper
public interface JobMapper extends BaseMapper<Job> {

    /**
     * 查询招聘中的职位列表
     */
    @Select("SELECT * FROM t_job WHERE status = 1 ORDER BY created_at DESC")
    List<Job> selectActiveJobs();

    /**
     * 查询用户创建的职位
     */
    @Select("SELECT * FROM t_job WHERE created_by = #{userId} ORDER BY created_at DESC")
    List<Job> selectByCreator(@Param("userId") Long userId);

    /**
     * 根据关键词搜索职位
     */
    @Select("SELECT * FROM t_job WHERE status = 1 AND " +
            "(title ILIKE CONCAT('%', #{keyword}, '%') OR " +
            "company ILIKE CONCAT('%', #{keyword}, '%') OR " +
            "description ILIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY created_at DESC")
    List<Job> searchByKeyword(@Param("keyword") String keyword);
}
