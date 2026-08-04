package com.ecommerce.productservice.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductStatusChangedEvent {
    private Long productId;
    private Integer oldStatus;  // null = 审核操作
    private Integer newStatus;
}
