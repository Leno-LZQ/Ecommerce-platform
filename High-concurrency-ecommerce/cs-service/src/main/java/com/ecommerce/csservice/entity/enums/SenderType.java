package com.ecommerce.csservice.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工单消息发送者类型
 */
@Getter
@AllArgsConstructor
public enum SenderType {
    USER("USER", "用户"),
    AGENT("AGENT", "客服"),
    SYSTEM("SYSTEM", "系统");

    private final String code;
    private final String desc;
}
