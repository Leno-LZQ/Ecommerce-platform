package com.ecommerce.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {

    /** 订单号 */
    private String orderNo;

    /** 原始总金额 */
    private BigDecimal totalAmount;

    /** 实付金额（优惠后） */
    private BigDecimal payAmount;
}
