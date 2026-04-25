package com.aiinterviewer.user.mapper;

import com.aiinterviewer.user.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查找用户
     */
    @Select("SELECT * FROM t_user WHERE username = #{username} AND deleted_at IS NULL")
    User findByUsername(@Param("username") String username);

    /**
     * 根据邮箱查找用户
     */
    @Select("SELECT * FROM t_user WHERE email = #{email} AND deleted_at IS NULL")
    User findByEmail(@Param("email") String email);

    /**
     * 根据手机号查找用户
     */
    @Select("SELECT * FROM t_user WHERE phone = #{phone} AND deleted_at IS NULL")
    User findByPhone(@Param("phone") String phone);

    /**
     * 根据用户名或邮箱或手机号查找用户
     */
    @Select("SELECT * FROM t_user WHERE (username = #{account} OR email = #{account} OR phone = #{account}) AND deleted_at IS NULL")
    User findByAccount(@Param("account") String account);

    /**
     * 获取用户角色列表
     */
    @Select("SELECT r.role_code FROM t_role r " +
            "INNER JOIN t_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> findRolesByUserId(@Param("userId") Long userId);
}
