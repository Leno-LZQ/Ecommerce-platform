package com.ecommerce.orderservice.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderShippedEvent {
    private String orderNo;
}
