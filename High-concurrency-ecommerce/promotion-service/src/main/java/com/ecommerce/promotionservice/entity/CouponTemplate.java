package com.ecommerce.promotionservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板表（coupon_template）
 *
 * 注意：本表有 create_time + update_time 但无 deleted 列，
 * 不继承 common BaseEntity，按需声明时间字段 + @TableField 自动填充。
 */
@Data
@TableName("coupon_template")
public class CouponTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 券名称
     */
    private String templateName;

    /**
     * 券类型（字符串枚举，与 MySQL ENUM 语义一致）：
     * FULL_REDUCTION / DISCOUNT / NO_THRESHOLD
     */
    private String couponType;

    /**
     * 优惠值：满减/无门槛=金额（元）；折扣=比例（0.80 = 8 折）
     */
    private BigDecimal discountValue;

    /**
     * 使用门槛金额（元），无门槛券为 0
     */
    private BigDecimal thresholdAmount;

    /**
     * 发行总量
     */
    private Integer totalQuantity;

    /**
     * 已领取数量（更新走原子 SQL：SET issued_quantity = issued_quantity + 1 WHERE id=? AND issued_quantity < total_quantity）
     */
    private Integer issuedQuantity;

    /**
     * 已核销数量
     */
    private Integer usedQuantity;

    /**
     * 每人限领数量
     */
    private Integer perUserLimit;

    /**
     * 有效天数（自领取时刻起算，写入 coupon_user.expire_time = NOW() + valid_days）
     */
    private Integer validDays;

    /**
     * 领取开始时间
     */
    private LocalDateTime startTime;

    /**
     * 领取结束时间
     */
    private LocalDateTime endTime;

    /**
     * 是否可与其他券叠加：0=否 1=是（Layer 4 裁决依据）
     */
    private Integer stackable;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 创建时间（MyMetaObjectHandler 自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间（MyMetaObjectHandler 自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
