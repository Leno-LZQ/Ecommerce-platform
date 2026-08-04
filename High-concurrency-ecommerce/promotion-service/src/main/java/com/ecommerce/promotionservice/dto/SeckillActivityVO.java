package com.ecommerce.promotionservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C 端秒杀场次视图（getSeckillSessions 返回）
 */
@Data
public class SeckillActivityVO {

    /** 秒杀活动 ID */
    private Long id;

    /** 活动名称 */
    private String activityName;

    /** 商品 ID */
    private Long productId;

    /** SKU ID */
    private Long skuId;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 原价（前端展示划线价，由调用方从 product-service 获取并合并） */
    private BigDecimal originPrice;

    /** 秒杀库存 */
    private Integer seckillStock;

    /** 每人限购 */
    private Integer perUserLimit;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态：0=即将开始 1=进行中 2=已结束 */
    private Integer status;

    /** 倒计时秒数（前端辅助，startTime - now，0 或负数时隐藏） */
    private Long countdownSeconds;
}
