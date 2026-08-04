package com.ecommerce.promotionservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 促销适用范围表（promotion_scope）—— 多态关联，无物理外键
 *
 * 约定：某 target 无任何 scope 记录 = 全场通用；
 * 有记录则行项须命中至少一条（商品直命中 PRODUCT、所属分类命中 CATEGORY、所属商家命中 MERCHANT）。
 *
 * 注意：本表无任何时间字段，不继承 BaseEntity。
 */
@Data
@TableName("promotion_scope")
public class PromotionScope {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 目标类型：COUPON_TEMPLATE / PROMOTION / SECKILL
     */
    private String targetType;

    /**
     * 目标 ID（对应 coupon_template.id / promotion_activity.id / seckill_activity.id）
     */
    private Long targetId;

    /**
     * 范围类型：CATEGORY / PRODUCT / MERCHANT
     */
    private String scopeType;

    /**
     * 适用范围 ID（分类/商品/商家 ID）
     */
    private Long scopeId;
}
