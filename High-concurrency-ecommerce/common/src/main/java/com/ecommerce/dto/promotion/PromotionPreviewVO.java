package com.ecommerce.dto.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 优惠预览结果（来自 promotion-service）。
 * 当前 promotion-service 未实现，先定义 stub。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionPreviewVO {

    /** 优惠类型：COUPON / FULL_REDUCTION / SECKILL */
    private String type;

    /** 优惠描述（前端展示用） */
    private String description;

    /** 优惠金额 */
    private BigDecimal discountAmount;
}
