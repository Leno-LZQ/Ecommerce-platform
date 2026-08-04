package com.ecommerce.productservice.event;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductPriceChangedEvent {
    private Long productId;
    private BigDecimal oldMinPrice;
    private BigDecimal newMinPrice;
}
