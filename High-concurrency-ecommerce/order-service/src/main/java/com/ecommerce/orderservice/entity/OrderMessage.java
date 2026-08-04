package com.ecommerce.orderservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_message")
public class OrderMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;
    private String messageBody;
    private String exchange;
    private String routingKey;
    private Integer status;
    private Integer retryCount;
    private Integer maxRetry;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
