package com.ecommerce.dto.promotion;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 购物车优惠预览请求（cart-service → /internal/promotion/preview）
 *
 * preview 与 lock 共用 DiscountEngine，差异仅在于：
 * - preview 的 couponCodes 可为空列表（只看活动优惠）
 * - preview 模式下引擎不执行任何 Redis/DB 写操作
 */
@Data
public class PreviewRequest {

    /** 用户 ID */
    @NotNull
    private Long userId;

    /** 购物车行项 */
    @NotEmpty
    private List<PromoCalcItem> items;

    /** 用户已选券码列表（可为空） */
    private List<String> couponCodes;
}
