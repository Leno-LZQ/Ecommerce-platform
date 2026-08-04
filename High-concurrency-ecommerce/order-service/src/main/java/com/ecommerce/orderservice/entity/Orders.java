package com.ecommerce.orderservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("orders")
public class Orders {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer status;
    private Integer isMultiMerchant;
    private Integer payType;
    private LocalDateTime payTime;
    private String consignee;
    private String phone;
    private String address;
    private String remark;
    private String cancelReason;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


    /** 订单明细（仅内存传递，不入 orders 表） */
    @TableField(exist = false)
    private List<OrderItem> items;
}
