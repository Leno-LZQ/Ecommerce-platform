package com.ecommerce.dto.promotion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 计算入参行项（preview / lock 共用）
 *
 * 入参携带 categoryId + merchantId，避免引擎内部回查 product-service，
 * 由调用方（order-service / cart-service）在组装请求时一并携带。
 */
@Data
public class PromoCalcItem {

    /** SKU ID */
    @NotNull
    private Long skuId;

    /** 商品 ID */
    @NotNull
    private Long productId;

    /** 所属商家 ID */
    @NotNull
    private Long merchantId;

    /** 所属分类 ID */
    @NotNull
    private Long categoryId;

    /** 行项单价（元） */
    @NotNull
    private BigDecimal price;

    /** 购买数量 */
    @NotNull
    @Min(1)
    private Integer quantity;
}
