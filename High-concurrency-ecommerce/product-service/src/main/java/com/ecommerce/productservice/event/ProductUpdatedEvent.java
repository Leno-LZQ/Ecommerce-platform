package com.ecommerce.productservice.event;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProductUpdatedEvent {
    private Long productId;
    private String name;
    private String description;
    private List<String> images;
}
