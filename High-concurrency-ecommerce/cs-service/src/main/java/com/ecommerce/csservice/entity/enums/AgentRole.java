package com.ecommerce.csservice.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客服角色
 */
@Getter
@AllArgsConstructor
public enum AgentRole {
    AGENT("AGENT", "客服专员"),
    SENIOR("SENIOR", "高级客服"),
    MANAGER("MANAGER", "客服主管");

    private final String code;
    private final String desc;
}
