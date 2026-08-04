package com.ecommerce.csservice.dto;

import lombok.Data;

/**
 * cs-service WebSocket 帧
 */
@Data
public class WebSocketFrame {

    /**
     * 帧类型：MESSAGE / ACK / TYPING / STATUS
     */
    private String type;

    private Long ticketId;

    private Long messageId;

    private Long senderId;

    private String senderType;

    private String content;

    private Long timestamp;
}
