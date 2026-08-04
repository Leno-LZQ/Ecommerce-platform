package com.ecommerce.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String sessionId;
    private Long expiresIn;
    private Boolean isNewUser;         // 是否新注册（前端弹窗引导改密）
    private String username;           // 系统分配的用户名
    private String nickname;
}
