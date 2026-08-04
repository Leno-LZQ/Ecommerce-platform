package com.ecommerce.Security;

import lombok.Data;

import java.util.List;

@Data
public class SessionData {
    private Long userId;
    private List<String> roles;
    private List<String> permissions;
    private Long merchantId;   // null = 普通用户
}

