package com.ecommerce.promotionservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C 端可领券视图（getAvailableTemplates 返回）
 *
 * remainingStock 为计算值（totalQuantity - issuedQuantity），
 * 实际扣减走 Redis + DB，此处仅展示。
 */
@Data
public class CouponTemplateVO {

    /** 模板 ID */
    private Long id;

    /** 券名称 */
    private String templateName;

    /** 券类型：FULL_REDUCTION / DISCOUNT / NO_THRESHOLD */
    private String couponType;

    /** 优惠值（满减/无门槛=金额；折扣=比例） */
    private BigDecimal discountValue;

    /** 使用门槛金额（无门槛券为 0） */
    private BigDecimal thresholdAmount;

    /** 剩余可领数量（totalQuantity - issuedQuantity，>=0 才出现在列表） */
    private Integer remainingStock;

    /** 每人限领数量 */
    private Integer perUserLimit;

    /** 当前用户已领数量（用于前端提示"已领 X/Y 张"） */
    private Integer userReceivedCount;

    /** 有效天数（前端展示"自领取起 N 天有效"） */
    private Integer validDays;

    /** 是否可叠加 */
    private Boolean stackable;

    /** 领取截止时间 */
    private LocalDateTime endTime;
}
