package com.ecommerce.promotionservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "promotion")
@Component
public class PromotionProperties {

    /** 券锁 TTL（秒），默认 900 = 15min。超时后 Redis 锁标记自动失效，由 LockTimeoutReleaseJob 兜底释放 */
    private int lockTtlSeconds = 900;

    /** 订单锁定明细 Hash TTL（秒），默认 1200 = 20min。
     *  必须 > lockTtlSeconds，保证锁标记过期后 Hash 还在，Job 能精确回补预算 */
    private int lockRecordTtlSeconds = 1200;

    /** 券库存 Redis Key 前缀 */
    private String couponStockKeyPrefix = "coupon:stock:";

    /** 新人注册时自动发放的券模板 ID 列表（管理端在 Nacos 配置后生效，重启或 @RefreshScope 热更新） */
    private List<Long> newUserTemplateIds = List.of();

    /** 秒杀子配置 */
    private Seckill seckill = new Seckill();

    @Data
    public static class Seckill {
        /** 开售前多少分钟把 seckill_stock 预热到 Redis seckill:stock:{id} */
        private int preloadBeforeMinutes = 10;
    }
}

