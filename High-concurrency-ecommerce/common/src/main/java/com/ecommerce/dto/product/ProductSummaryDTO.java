package com.ecommerce.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品摘要（供 search-service / 其他服务 Feign 调用）。
 * <p>
 * 与 product-service 内部的 ProductResponse 不同，这是跨服务共享的精简视图，
 * 只包含 ES 索引同步等场景需���的最小字段集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryDTO {

    private Long id;
    private String name;
    private Long categoryId;
    private String categoryName;
    private Long merchantId;
    private String mainImage;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer status;
    private Integer auditStatus;
    private Long sales;
    private LocalDateTime createTime;
}
