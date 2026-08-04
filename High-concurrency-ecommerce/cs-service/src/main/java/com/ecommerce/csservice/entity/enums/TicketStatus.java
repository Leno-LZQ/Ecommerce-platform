package com.ecommerce.csservice.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工单状态机
 * CREATED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED
 *                    ↗ REOPEN
 */
@Getter
@AllArgsConstructor
public enum TicketStatus {
    CREATED("CREATED", "已创建"),
    ASSIGNED("ASSIGNED", "已分配"),
    IN_PROGRESS("IN_PROGRESS", "处理中"),
    RESOLVED("RESOLVED", "已解决"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String desc;

    public static TicketStatus of(String code) {
        for (TicketStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
