package com.ecommerce.promotionservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 适用范围请求体
 *
 * 被 CouponTemplateRequest / PromotionActivityRequest / SeckillActivityRequest 内嵌使用，
 * 也可独立作为 scope 批量管理接口的请求体。
 */
@Data
public class ScopeRequest {

    /** 范围类型：CATEGORY / PRODUCT / MERCHANT */
    @NotBlank(message = "范围类型不能为空")
    private String scopeType;

    /** 适用范围 ID（分类/商品/商家 ID） */
    @NotNull(message = "范围 ID 不能为空")
    private Long scopeId;
}
