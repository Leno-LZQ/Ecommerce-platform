package com.ecommerce.orderservice.event;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderPaidEvent {
    private String orderNo;
    private Long orderId;
    private Long merchantId;
    private Long userId;
    private BigDecimal payAmount;
    private Integer payType;
}
