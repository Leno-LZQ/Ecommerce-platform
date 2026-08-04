package com.ecommerce.csservice.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客服在线状态
 */
@Getter
@AllArgsConstructor
public enum AgentStatus {
    ONLINE("ONLINE", "在线"),
    OFFLINE("OFFLINE", "离线"),
    BUSY("BUSY", "忙碌");

    private final String code;
    private final String desc;
}
