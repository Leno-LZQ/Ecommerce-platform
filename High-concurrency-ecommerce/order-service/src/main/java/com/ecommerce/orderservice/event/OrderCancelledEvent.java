package com.ecommerce.orderservice.event;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderCancelledEvent {
    private String orderNo;
    private Long userId;
    private String cancelReason;
}
