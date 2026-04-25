package com.aiinterviewer.user.service.impl;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.common.util.JwtUtils;
import com.aiinterviewer.common.util.SecurityUtils;
import com.aiinterviewer.user.dto.*;
import com.aiinterviewer.user.entity.User;
import com.aiinterviewer.user.service.AuthService;
import com.aiinterviewer.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private static final String TOKEN_BLACKLIST_PREFIX = "auth:blacklist:";

    @Override
    public LoginResponse login(LoginRequest request, String clientIp) {
        // 查找用户
        User user = userService.getByAccount(request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 检查用户状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 验证密码
        if (!SecurityUtils.matchPassword(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // 获取用户角色
        List<String> roles = userService.getUserRoles(user.getId());

        // 生成Token
        JwtUtils jwtUtils = new JwtUtils(jwtSecret, accessTokenExpiration, refreshTokenExpiration);
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getUsername());

        // 更新最后登录信息
        userService.updateLastLogin(user.getId(), clientIp);

        log.info("用户登录成功: id={}, username={}, ip={}", user.getId(), user.getUsername(), clientIp);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(userService.toDTO(user))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO register(RegisterRequest request) {
        // 验证密码一致性
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "两次密码输入不一致");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(SecurityUtils.encryptPassword(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());

        user = userService.createUser(user);

        log.info("用户注册成功: id={}, username={}", user.getId(), user.getUsername());

        return userService.toDTO(user);
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        JwtUtils jwtUtils = new JwtUtils(jwtSecret, accessTokenExpiration, refreshTokenExpiration);

        // 验证刷新Token
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // 检查Token类型
        String tokenType = jwtUtils.getTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "无效的刷新令牌");
        }

        // 检查是否在黑名单中
        if (isTokenBlacklisted(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "令牌已失效");
        }

        // 获取用户信息
        Long userId = jwtUtils.getUserId(refreshToken);
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 获取用户角色
        List<String> roles = userService.getUserRoles(user.getId());

        // 生成新Token
        String newAccessToken = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), roles);
        String newRefreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getUsername());

        // 将旧的刷新Token加入黑名单
        blacklistToken(refreshToken, jwtUtils.getTokenRemainingTime(refreshToken));

        log.info("Token刷新成功: userId={}", userId);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(userService.toDTO(user))
                .build();
    }

    @Override
    public void logout(String accessToken) {
        JwtUtils jwtUtils = new JwtUtils(jwtSecret, accessTokenExpiration, refreshTokenExpiration);

        if (jwtUtils.validateToken(accessToken)) {
            long remainingTime = jwtUtils.getTokenRemainingTime(accessToken);
            blacklistToken(accessToken, remainingTime);
            log.info("用户登出成功");
        }
    }

    @Override
    public boolean validateToken(String token) {
        if (isTokenBlacklisted(token)) {
            return false;
        }

        JwtUtils jwtUtils = new JwtUtils(jwtSecret, accessTokenExpiration, refreshTokenExpiration);
        return jwtUtils.validateToken(token);
    }

    /**
     * 将Token加入黑名单
     */
    private void blacklistToken(String token, long ttl) {
        String key = TOKEN_BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", ttl, TimeUnit.MILLISECONDS);
    }

    /**
     * 检查Token是否在黑名单中
     */
    private boolean isTokenBlacklisted(String token) {
        String key = TOKEN_BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
