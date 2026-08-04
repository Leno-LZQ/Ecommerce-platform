package com.ecommerce.paymentservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment")
public class Payment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 支付单号 */
    private String payNo;
    /** 关联订单号（→ orders.order_no, RESTRICT） */
    private String orderNo;
    /** 支付用户ID（→ user.id, RESTRICT） */
    private Long userId;
    /** 支付金额 */
    private BigDecimal totalAmount;
    /** 支付方式：1=支付宝 2=微信 */
    private Integer payType;
    /** 支付状态：0=待支付 1=支付成功 2=支付失败 3=已退款 */
    private Integer status;
    /** 支付回调时间 */
    private LocalDateTime callbackTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
