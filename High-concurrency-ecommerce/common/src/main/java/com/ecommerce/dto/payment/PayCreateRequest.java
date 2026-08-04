package com.ecommerce.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建支付单请求（order-service Saga 调用）。
 */
@Data
public class PayCreateRequest {

    /** 订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 用户ID */
    @NotNull(message = "用户ID不能为空")
    @Positive
    private Long userId;

    /** 支付金额 */
    @NotNull(message = "支付金额不能为空")
    @Positive
    private BigDecimal payAmount;
}
