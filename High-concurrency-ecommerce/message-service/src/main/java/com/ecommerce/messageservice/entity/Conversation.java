package com.ecommerce.messageservice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long merchantId;
    private Long productId;
    private Long orderId;
    private String lastMessage;
    private Integer unreadUser;
    private Integer unreadMerchant;
    private Integer status;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
