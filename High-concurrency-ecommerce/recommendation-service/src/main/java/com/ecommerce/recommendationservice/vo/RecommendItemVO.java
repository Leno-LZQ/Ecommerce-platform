package com.ecommerce.recommendationservice.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 推荐结果项（回填商品信息后返回给前端）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendItemVO {

    private Long productId;
    private String productName;
    private String mainImage;
    private Long merchantId;
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    /** 推荐来源策略 */
    private String strategy;

    /** 推荐分数（仅内部展示/调试） */
    private BigDecimal score;

}
