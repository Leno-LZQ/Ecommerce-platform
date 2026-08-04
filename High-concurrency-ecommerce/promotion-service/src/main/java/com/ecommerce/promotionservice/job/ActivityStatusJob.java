package com.ecommerce.promotionservice.job;

import com.ecommerce.promotionservice.entity.PromotionActivity;
import com.ecommerce.promotionservice.entity.SeckillActivity;
import com.ecommerce.promotionservice.mapper.PromotionActivityMapper;
import com.ecommerce.promotionservice.mapper.SeckillActivityMapper;
import com.ecommerce.promotionservice.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动/秒杀状态机推进 + 秒杀库存预热 定时任务。
 *
 * 每分钟执行：
 * - 推进 promotion_activity / seckill_activity 的 status（0→1→2）
 * - 秒杀开场前预热库存到 Redis
 * - 活动开始时把 remaining_budget 刷入 Redis promo:budget
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityStatusJob {

    private final PromotionActivityMapper activityMapper;
    private final SeckillActivityMapper seckillMapper;
    private final SeckillService seckillService;
    private final StringRedisTemplate redisTemplate;

    private static final String BUDGET_PREFIX = "promo:budget:";

    @Scheduled(cron = "0 * * * * *")
    public void run() {
        LocalDateTime now = LocalDateTime.now();

        // === 1. 推进活动状态 ===
        List<PromotionActivity> allActivities = activityMapper.selectList(null);
        for (PromotionActivity act : allActivities) {
            if (act.getStatus() == null) continue;

            Integer newStatus = null;
            if (act.getStatus() == 0 && act.getStartTime() != null && !act.getStartTime().isAfter(now)) {
                newStatus = 1; // 未开始 → 进行中
            } else if (act.getStatus() == 1 && act.getEndTime() != null && act.getEndTime().isBefore(now)) {
                newStatus = 2; // 进行中 → 已结束
            }

            if (newStatus != null) {
                activityMapper.updateStatus(act.getId(), newStatus);
                log.info("活动状态推进 id={} {}→{}", act.getId(), act.getStatus(), newStatus);

                // 活动开始时预算预热到 Redis
                if (newStatus == 1 && act.getTotalBudget() != null) {
                    long budgetCents = act.getRemainingBudget() != null
                        ? act.getRemainingBudget().multiply(BigDecimal.valueOf(100)).longValue()
                        : act.getTotalBudget().multiply(BigDecimal.valueOf(100)).longValue();
                    String key = BUDGET_PREFIX + act.getId();
                    redisTemplate.opsForValue().set(key, String.valueOf(budgetCents));
                    if (act.getEndTime() != null) {
                        long ttl = Duration.between(now, act.getEndTime()).getSeconds();
                        if (ttl > 0) redisTemplate.expire(key, Duration.ofSeconds(ttl));
                    }
                }
            }
        }

        // === 2. 推进秒杀状态 + 库存预热 ===
        List<SeckillActivity> allSeckills = seckillMapper.selectList(null);
        for (SeckillActivity sa : allSeckills) {
            if (sa.getStatus() == null) continue;

            Integer newStatus = null;
            if (sa.getStatus() == 0 && sa.getStartTime() != null && !sa.getStartTime().isAfter(now)) {
                newStatus = 1; // 未开始 → 进行中
            } else if (sa.getStatus() == 1 && sa.getEndTime() != null && sa.getEndTime().isBefore(now)) {
                newStatus = 2; // 进行中 → 已结束
            }

            if (newStatus != null) {
                seckillMapper.updateStatus(sa.getId(), newStatus);
                log.info("秒杀状态推进 id={} {}→{}", sa.getId(), sa.getStatus(), newStatus);

                // 秒杀开始时预热库存
                if (newStatus == 1) {
                    seckillService.preloadStock(sa.getId());
                }
                // 秒杀结束时清理库存
                if (newStatus == 2) {
                    seckillService.cleanupStock(sa.getId());
                }
            }
        }
    }
}
