package com.ecommerce.promotionservice.job;

import com.ecommerce.promotionservice.entity.CouponUser;
import com.ecommerce.promotionservice.mapper.CouponUserMapper;
import com.ecommerce.promotionservice.service.PromotionLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 锁超时释放定时任务。
 *
 * 每 5 分钟扫描 coupon_user.status=1 的记录，
 * 若 coupon:lock:{code} 已不存在（TTL 到期 = 支付超时），
 * 则走 release 逻辑释放券和预算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LockTimeoutReleaseJob {

    private final CouponUserMapper couponUserMapper;
    private final PromotionLockService promotionLockService;
    private final StringRedisTemplate redisTemplate;

    private static final String COUPON_LOCK_PREFIX = "coupon:lock:";
    private static final int BATCH_SIZE = 100;

    @Scheduled(cron = "0 */5 * * * *")
    public void scan() {
        log.debug("启动锁超时释放扫描");
        long offset = 0;
        while (true) {
            List<CouponUser> locked = couponUserMapper.selectLocked(offset, BATCH_SIZE);
            if (locked.isEmpty()) break;

            for (CouponUser cu : locked) {
                String lockKey = COUPON_LOCK_PREFIX + cu.getCouponCode();
                Boolean exists = redisTemplate.hasKey(lockKey);
                if (Boolean.FALSE.equals(exists) || exists == null) {
                    // 券锁已过期 → 释放
                    try {
                        promotionLockService.release(cu.getLockOrderNo());
                        log.info("自动释放超时锁定 orderNo={} couponCode={}",
                            cu.getLockOrderNo(), cu.getCouponCode());
                    } catch (Exception e) {
                        log.error("自动释放超时锁定失败 orderNo={}", cu.getLockOrderNo(), e);
                    }
                }
            }
            offset += BATCH_SIZE;
        }
    }
}
