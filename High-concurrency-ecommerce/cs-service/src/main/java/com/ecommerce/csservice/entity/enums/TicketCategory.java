package com.ecommerce.csservice.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工单分类：用于匹配客服专长领域
 */
@Getter
@AllArgsConstructor
public enum TicketCategory {
    ORDER_DISPUTE("ORDER_DISPUTE", "订单纠纷"),
    REFUND("REFUND", "退款申诉"),
    ACCOUNT("ACCOUNT", "账号问题"),
    COMPLAINT("COMPLAINT", "投诉举报"),
    GENERAL("GENERAL", "一般咨询");

    private final String code;
    private final String desc;

    public static TicketCategory of(String code) {
        for (TicketCategory value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
