package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductUpdateRequest {
    @Size(max = 200)
    private String name;

    private String description;
    private Long categoryId;
    private String brand;
    private String mainImage;

    @Size(max = 10)
    private List<String> images;

    private List<SkuRequest> skus; // 可选更新SKU（null=不更新SKU）
    private String promoTag;
    private LocalDateTime promoEndTime;
}
