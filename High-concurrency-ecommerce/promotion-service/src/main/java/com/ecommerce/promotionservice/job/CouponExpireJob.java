package com.ecommerce.promotionservice.job;

import com.ecommerce.promotionservice.entity.CouponUser;
import com.ecommerce.promotionservice.mapper.CouponUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 券过期扫描定时任务。
 *
 * 每小时扫描 coupon_user.status=0 且 expire_time < now 的记录，
 * 标记为 status=3（已过期）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpireJob {

    private final CouponUserMapper couponUserMapper;

    private static final int BATCH_SIZE = 500;

    @Scheduled(cron = "0 0 * * * *")
    public void scan() {
        log.debug("启动券过期扫描");
        LocalDateTime now = LocalDateTime.now();

        while (true) {
            List<CouponUser> expired = couponUserMapper.selectExpired(now, BATCH_SIZE);
            if (expired.isEmpty()) break;

            for (CouponUser cu : expired) {
                try {
                    int rows = couponUserMapper.markExpired(cu.getId());
                    if (rows > 0) {
                        log.debug("券过期标记 id={} code={}", cu.getId(), cu.getCouponCode());
                    }
                } catch (Exception e) {
                    log.error("券过期标记失败 id={}", cu.getId(), e);
                }
            }
        }
    }
}
