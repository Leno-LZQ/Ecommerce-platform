package com.ecommerce.productservice.event;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductCreatedEvent {
    private Long productId;
    private Long merchantId;
    private String name;
    private Long categoryId;
    private BigDecimal minPrice;
    private LocalDateTime createTime;
}
