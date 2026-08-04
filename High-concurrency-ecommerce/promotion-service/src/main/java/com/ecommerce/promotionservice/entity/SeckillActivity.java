package com.ecommerce.promotionservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动表（seckill_activity）—— SKU 级独立价格 + 独立库存
 *
 * 注意：本表只有 create_time，不继承 BaseEntity。
 */
@Data
@TableName("seckill_activity")
public class SeckillActivity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 秒杀活动名称
     */
    private String activityName;

    /**
     * 秒杀商品 ID
     */
    private Long productId;

    /**
     * 秒杀 SKU ID
     */
    private Long skuId;

    /**
     * 秒杀价（元），Layer 1 直接替换行项原价
     */
    private BigDecimal seckillPrice;

    /**
     * 秒杀库存（Redis seckill:stock:{id} 预热镜像，扣减走 Lua）
     */
    private Integer seckillStock;

    /**
     * 每人限购数量
     */
    private Integer perUserLimit;

    /**
     * 秒杀开始时间
     */
    private LocalDateTime startTime;

    /**
     * 秒杀结束时间
     */
    private LocalDateTime endTime;

    /**
     * 状态：0=未开始 1=进行中 2=已结束
     * （由 ActivityStatusJob 推进；开售前预热 seckill:stock 到 Redis）
     */
    private Integer status;

    /**
     * 创建时间（MyMetaObjectHandler 自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
