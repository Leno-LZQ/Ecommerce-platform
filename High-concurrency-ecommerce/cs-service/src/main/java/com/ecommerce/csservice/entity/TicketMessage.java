package com.ecommerce.csservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单消息记录表
 */
@Data
@TableName(value = "ticket_message", autoResultMap = true)
public class TicketMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long ticketId;

    private String senderType;

    private Long senderId;

    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentUrls;

    @TableField("created_at")
    private LocalDateTime createTime;
}
