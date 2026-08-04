package com.ecommerce.promotionservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的券视图（getMyCoupons 返回，含券面信息 JOIN）
 */
@Data
public class CouponUserVO {

    /** 券码（对外标识，非递增、防枚举） */
    private String couponCode;

    /** 模板 ID */
    private Long templateId;

    /** 券名称（JOIN coupon_template） */
    private String templateName;

    /** 券类型 */
    private String couponType;

    /** 优惠值 */
    private BigDecimal discountValue;

    /** 使用门槛 */
    private BigDecimal thresholdAmount;

    /** 券状态：0=未使用 1=已锁定 2=已使用 3=已过期 4=已退还 */
    private Integer status;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 使用时间（已使用时才有） */
    private LocalDateTime useTime;

    /** 领取时间 */
    private LocalDateTime createTime;

    /** 是否可叠加 */
    private Boolean stackable;

    // ---- 前端辅助字段（由 service 层计算，不存库） ----

    /** 剩余有效天数（>=0 时展示"还剩X天"，<0 隐藏） */
    private Long remainingDays;
}
