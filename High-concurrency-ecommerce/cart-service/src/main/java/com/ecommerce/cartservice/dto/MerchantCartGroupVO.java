package com.ecommerce.cartservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 同一商家下的购物车分组。
 * <p>
 * 对应 CartService#getCart() 中按 merchantId 分组后的结果单元。
 * 用于前端按店铺维度渲染（店铺名、店铺小计、店铺级优惠等）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantCartGroupVO {

    /** 商家 ID */
    private Long merchantId;

    /** 商家名称（可选，由商家服务回填，前端可不依赖） */
    private String merchantName;

    /** 该商家下的所有购物车条目（含未选中） */
    private List<CartItemVO> items;

    /** 该商家下的商品 SKU 数量（= items.size()） */
    private Integer itemCount;

    /** 该商家下被勾选的商品件数（仅 checked=true 计入） */
    private Integer selectedCount;

    /** 该商家下所有商品的原价合计（含未选中） */
    private java.math.BigDecimal totalPrice;

    /** 该商家下被勾选商品的小计（用于结算按钮金额汇总） */
    private java.math.BigDecimal selectedPrice;

    /** 该商家可用的优惠预览（来自 promotion-service） */
    private List<com.ecommerce.dto.promotion.PromotionPreviewVO> promotions;

    /** 该商家下的优惠抵扣金额合计 */
    private BigDecimal discountAmount;
}
