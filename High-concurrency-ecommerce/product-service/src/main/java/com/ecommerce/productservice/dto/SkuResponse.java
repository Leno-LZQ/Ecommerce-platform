package com.ecommerce.productservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class SkuResponse {
    private Long id;
    private Map<String, String> attrs;  // 规格属性
    private BigDecimal price;
    private Integer stock;              // 合并后的库存（优先 Redis 实时库存）
    // ⚠️ 不返回 version（乐观锁内部字段）
}
