package com.ecommerce.messageservice.dto;

import com.ecommerce.messageservice.entity.enums.FrameType;
import com.ecommerce.messageservice.entity.enums.MessageType;
import lombok.Data;

@Data
public class WebSocketFrame {
    FrameType type;           // 帧类型：MESSAGE / READ_ACK / TYPING / ACK
    Long conversationId;      // 会话 ID
    Long messageId;           // 消息 ID（ACK 帧用）
    String content;           // 消息内容
    MessageType messageType;  // 消息类型（TEXT / IMAGE / ...）
    Object payload;           // 附加数据（商品卡片、订单卡片的 JSON）
    Long timestamp;
}
