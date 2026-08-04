package com.ecommerce.promotionservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 促销活动定义表（promotion_activity）
 *
 * 注意：本表有 create_time + update_time 但无 deleted 列，不继承 BaseEntity。
 */
@Data
@TableName("promotion_activity")
public class PromotionActivity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 活动名称
     */
    private String activityName;

    /**
     * 活动类型（字符串枚举）：
     * FULL_REDUCTION / FULL_DISCOUNT / N_M
     */
    private String activityType;

    /**
     * 总预算（元），NULL = 不限预算（不限时跳过预算校验与 Redis 预扣）
     */
    private BigDecimal totalBudget;

    /**
     * 剩余预算（元），NULL 表示不限；
     * Redis promo:budget:{id} 以分为单位实时镜像，MySQL 定期对账回刷
     */
    private BigDecimal remainingBudget;

    /**
     * 每人每天参与上限，NULL = 不限
     */
    private Integer userDailyLimit;

    /**
     * 活动开始时间
     */
    private LocalDateTime startTime;

    /**
     * 活动结束时间
     */
    private LocalDateTime endTime;

    /**
     * 状态：0=未开始 1=进行中 2=已结束
     * （由 ActivityStatusJob 定时推进，Layer 2 只认 status=1）
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
