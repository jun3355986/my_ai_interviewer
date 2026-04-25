package com.aiinterviewer.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 更新用户请求
 */
@Data
public class UpdateUserRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 昵称
     */
    @Size(max = 50, message = "昵称长度不超过50个字符")
    private String nickname;

    /**
     * 头像URL
     */
    @Size(max = 500, message = "头像URL长度不超过500个字符")
    private String avatarUrl;

    /**
     * 手机号
     */
    @Size(max = 20, message = "手机号长度不超过20个字符")
    private String phone;
}
