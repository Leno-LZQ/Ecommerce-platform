package com.ecommerce.dto.promotion;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 下单锁定优惠请求（order-service Saga Step 2.5 → /internal/promotion/lock）
 *
 * ⚠️ 契约对齐点：order-service 文档中字段名为 couponIds，
 * 但物理模型对外标识是 coupon_code（字符串券码），此处统一用 couponCodes。
 * 联调前与 order 侧确认最终字段名。
 */
@Data
public class PromotionLockRequest {

    /** 订单号（幂等键） */
    @NotNull
    private String orderNo;

    /** 用户 ID */
    @NotNull
    private Long userId;

    /** 订单行项 */
    @NotEmpty
    private List<PromoCalcItem> items;

    /** 用户选用的券码列表 */
    private List<String> couponCodes;
}
