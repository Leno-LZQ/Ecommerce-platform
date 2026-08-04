package com.ecommerce.messageservice.entity.enums;

public enum FrameType {

    MESSAGE,      // 普通消息
    READ_ACK,     // 已读回执
    TYPING,       // 正在输入
    ACK;          // 服务端收到确认

}
