package com.aiinterviewer.user.service;

import com.aiinterviewer.user.dto.UserDTO;
import com.aiinterviewer.user.entity.User;

import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 根据ID获取用户
     */
    User getById(Long id);

    /**
     * 根据用户名获取用户
     */
    User getByUsername(String username);

    /**
     * 根据邮箱获取用户
     */
    User getByEmail(String email);

    /**
     * 根据账号（用户名/邮箱/手机号）获取用户
     */
    User getByAccount(String account);

    /**
     * 创建用户
     */
    User createUser(User user);

    /**
     * 更新用户
     */
    User updateUser(User user);

    /**
     * 获取用户角色列表
     */
    List<String> getUserRoles(Long userId);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 更新最后登录信息
     */
    void updateLastLogin(Long userId, String ip);

    /**
     * 转换为DTO
     */
    UserDTO toDTO(User user);
}
