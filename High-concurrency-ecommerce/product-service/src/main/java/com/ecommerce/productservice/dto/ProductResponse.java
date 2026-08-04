package com.ecommerce.productservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductResponse {
    private Long id;
    private String name;
    private String description;        // 富文本描述
    private Long categoryId;
    private String categoryName;       // 分类名称（关联查询）
    private String brand;
    private String mainImage;
    private List<String> images;       // 所有图片
    private Integer status;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Long sales;
    private String promoTag;
    private LocalDateTime promoEndTime;
    private List<SkuResponse> skus;          // SKU 列表（含实时库存）
    private ReviewSummaryResponse reviewSummary; // 评价汇总
    private LocalDateTime createTime;
}
