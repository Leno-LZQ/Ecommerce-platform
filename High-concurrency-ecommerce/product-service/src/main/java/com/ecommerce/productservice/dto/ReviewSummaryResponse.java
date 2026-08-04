package com.ecommerce.productservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ReviewSummaryResponse {
    private BigDecimal averageScore;       // 平均分（保留1位小数）
    private Integer totalCount;            // 总评价数
    private Map<Integer, Integer> scoreDistribution;  // 各星级数量 {1:3, 2:5, 3:20, 4:50, 5:100}
    private Integer goodRate;              // 好评率（4-5星所占百分比）
}
