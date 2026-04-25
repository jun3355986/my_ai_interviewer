package com.aiinterviewer.user.service.impl;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.common.util.SecurityUtils;
import com.aiinterviewer.user.dto.UserDTO;
import com.aiinterviewer.user.entity.User;
import com.aiinterviewer.user.mapper.UserMapper;
import com.aiinterviewer.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    @Override
    public User getByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    @Override
    public User getByAccount(String account) {
        return userMapper.findByAccount(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createUser(User user) {
        // 检查用户名是否存在
        if (existsByUsername(user.getUsername())) {
            throw new BusinessException(ErrorCode.USER_EXISTS, "用户名已存在");
        }

        // 检查邮箱是否存在
        if (existsByEmail(user.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        // 设置默认值
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);
        log.info("用户创建成功: id={}, username={}", user.getId(), user.getUsername());

        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateUser(User user) {
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return user;
    }

    @Override
    public List<String> getUserRoles(Long userId) {
        return userMapper.findRolesByUserId(userId);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.findByUsername(username) != null;
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMapper.findByEmail(email) != null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLastLogin(Long userId, String ip) {
        User user = new User();
        user.setId(userId);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public UserDTO toDTO(User user) {
        if (user == null) {
            return null;
        }

        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(SecurityUtils.maskPhone(user.getPhone()));
        dto.setNickname(user.getNickname());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setStatus(user.getStatus());
        dto.setLastLoginTime(user.getLastLoginTime());
        dto.setCreatedAt(user.getCreatedAt());

        // 获取角色
        List<String> roles = getUserRoles(user.getId());
        dto.setRoles(roles);

        return dto;
    }
}
