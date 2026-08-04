package com.ecommerce.productservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductListResponse {
    private Long id;
    private String name;
    private String mainImage;        // 主图
    private BigDecimal minPrice;     // 最低价
    private BigDecimal maxPrice;     // 最高价
    private Long sales;              // 销量
    private String promoTag;         // 营销标签
    private LocalDateTime promoEndTime;
}
