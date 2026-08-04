package com.ecommerce.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * SKU 详情（供内部服务间查询，如 cart-service 加购时获取快照信息）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuDetailDTO {

    /** SKU ID */
    private Long skuId;

    /** 所属 SPU ID */
    private Long productId;

    /** 所属分类 ID（促销引擎行项计算用） */
    private Long categoryId;

    /** 商品名称 */
    private String productName;

    /** SKU 价格 */
    private BigDecimal price;

    /** 商品主图（取 Product.mainImage） */
    private String image;

    /** 商家 ID */
    private Long merchantId;

    /** 实时库存（来自 inventory-service，可能为 null 表示未查询） */
    private Integer stock;
}
