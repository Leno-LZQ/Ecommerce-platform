package com.ecommerce.dto.message;

import lombok.Data;

/**
 * 系统消息通知请求（供 Feign 调用方使用，放 common 模块）
 */
@Data
public class SystemNotifyRequest {

    /** 买家 ID（消息接收方） */
    private Long userId;

    /** 商家 ID */
    private Long merchantId;

    /** 关联商品（可选） */
    private Long productId;

    /** 关联订单（可选） */
    private Long orderId;

    /** 系统消息内容 */
    private String content;
}
