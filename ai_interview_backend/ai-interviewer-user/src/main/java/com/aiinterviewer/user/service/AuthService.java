package com.aiinterviewer.user.service;

import com.aiinterviewer.user.dto.LoginRequest;
import com.aiinterviewer.user.dto.LoginResponse;
import com.aiinterviewer.user.dto.RegisterRequest;
import com.aiinterviewer.user.dto.UserDTO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /**
     * 用户注册
     */
    UserDTO register(RegisterRequest request);

    /**
     * 刷新Token
     */
    LoginResponse refreshToken(String refreshToken);

    /**
     * 用户登出
     */
    void logout(String accessToken);

    /**
     * 验证Token
     */
    boolean validateToken(String token);
}
