package com.aiinterviewer.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 刷新Token请求
 */
@Data
public class RefreshTokenRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
