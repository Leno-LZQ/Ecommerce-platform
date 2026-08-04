package com.ecommerce.messageservice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ecommerce.messageservice.entity.enums.MessageType;
import com.ecommerce.messageservice.entity.enums.SenderType;
import org.apache.ibatis.type.EnumTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "message", autoResultMap = true)
public class Message {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;

    @TableField(typeHandler = EnumTypeHandler.class)
    private SenderType senderType;

    private Long senderId;

    @TableField(typeHandler = EnumTypeHandler.class)
    private MessageType messageType;
    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentUrls;

    private Integer isRead;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
