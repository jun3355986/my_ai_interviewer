package com.aiinterviewer.resume.mapper;

import com.aiinterviewer.resume.entity.Resume;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 简历Mapper接口
 */
@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {

    /**
     * 取消用户的默认简历状态
     */
    @Update("UPDATE t_resume SET is_default = false WHERE user_id = #{userId}")
    int clearDefaultByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的简历列表
     */
    @Select("SELECT * FROM t_resume WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Resume> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的默认简历
     */
    @Select("SELECT * FROM t_resume WHERE user_id = #{userId} AND is_default = true ORDER BY updated_at DESC LIMIT 1")
    Resume selectDefaultByUserId(@Param("userId") Long userId);
}
