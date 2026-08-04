package com.ecommerce.cartservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.ecommerce.dto.promotion.PromotionPreviewVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 完整购物车响应。含按商家分组的商品列表、金额汇总、优惠预览。
 * <p>
 * 由 CartService#getCart() 组装后返回给 controller / 前端。
 * 字段约定：
 * <ul>
 *   <li>total*   描述购物车全量（含未选中）</li>
 *   <li>selected* 描述当前勾选状态（结算时使用）</li>
 *   <li>discount* / payable* 优惠与应付</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartVO {

    /** 所属用户 ID */
    private Long userId;

    /** 按商家分组的购物车条目 */
    private List<MerchantCartGroupVO> merchantGroups;

    /** 全部商品件数（= 所有分组 itemCount 求和） */
    private Integer totalQuantity;

    /** 当前被勾选的总件数（= 所有分组 selectedCount 求和） */
    private Integer selectedQuantity;

    /** 全部商品原价合计 */
    private BigDecimal totalPrice;

    /** 勾选商品的小计（结算依据） */
    private BigDecimal selectedPrice;

    /** 全局可用的优惠预览（跨店满减、平台券等） */
    private List<PromotionPreviewVO> promotions;

    /** 优惠抵扣总金额（商家级 + 全局级） */
    private BigDecimal discountAmount;

    /** 应付金额 = selectedPrice - discountAmount（不小于 0） */
    private BigDecimal payableAmount;

    /** 购物车最近一次更新时间（任一字段变更即刷新） */
    private LocalDateTime updatedAt;
}
