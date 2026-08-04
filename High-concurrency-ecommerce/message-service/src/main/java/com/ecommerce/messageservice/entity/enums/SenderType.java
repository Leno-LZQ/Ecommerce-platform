package com.ecommerce.messageservice.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SenderType {
    USER(1, "用户"),
    MERCHANT(2, "商家"),
    SYSTEM(3, "系统");

    private final int code;
    private final String desc;
}
