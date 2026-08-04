package com.ecommerce.promotionservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 促销阶梯规则表（promotion_rule）
 *
 * 注意：本表无任何时间字段，不继承 BaseEntity，也不声明任何 @TableField(fill=...)。
 */
@Data
@TableName("promotion_rule")
public class PromotionRule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属活动 ID（→ promotion_activity，CASCADE 删除）
     */
    private Long activityId;

    /**
     * 最低金额（或件数）门槛，NULL 表示下不封顶
     */
    private BigDecimal minAmount;

    /**
     * 最高金额（或件数）门槛，NULL 表示上不封顶
     */
    private BigDecimal maxAmount;

    /**
     * 优惠类型：FIXED_AMOUNT（减 N 元）/ PERCENTAGE（打 N 折，0.80）/ FIXED_PRICE（N 元 M 件的打包价 N）
     */
    private String discountType;

    /**
     * 优惠值（与 discount_type 对应：金额=元、比例=0.80、打包价=元）
     */
    private BigDecimal discountValue;

    /**
     * 阶梯顺序（用于同活动多规则排序）
     */
    private Integer sortOrder;
}
