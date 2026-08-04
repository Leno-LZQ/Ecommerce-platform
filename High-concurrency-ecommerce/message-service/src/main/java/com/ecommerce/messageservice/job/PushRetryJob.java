package com.ecommerce.messageservice.job;

import com.ecommerce.messageservice.service.PushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PushRetryJob 离线推送定时重试任务
 *
 * <p>定时扫描 push_record 表，找出"推送失败"的记录，重新尝试推送。</p>
 *
 * <p>为什么需要定时任务？</p>
 * <ul>
 *   <li>用户 A 给用户 B 发消息时 B 不在线 → 写 push_record（pushStatus=0）</li>
 *   <li>B 上线时 → PushService.pushOfflineMessages() 推送（pushStatus→1）</li>
 *   <li>但 B 上线瞬间网络抖动导致推送失败 → pushStatus=2</li>
 *   <li>没有定时任务的话，这条消息就永远卡在 pushStatus=2 了</li>
 * </ul>
 *
 * <p>执行频率：每 30 秒一次。这个频率既能及时重试，又不会给数据库造成压力。</p>
 * <p>最大重试次数：3 次。超过后不再重试，需要人工介入或告警。</p>
 */
@Slf4j
@Component
public class PushRetryJob {

    private final PushService pushService;

    // 最大重试次数
    private static final int MAX_RETRY = 3;

    public PushRetryJob(PushService pushService) {
        this.pushService = pushService;
    }

    /**
     * 定时重试——每 30 秒触发一次
     *
     * <p>fixedRate = 30,000 毫秒：任务开始后每 30 秒触发一次，
     * 不管上一次是否执行完。如果某次执行超过 30 秒，下一次会在完成后立即触发。</p>
     *
     * <p>对比 fixedDelay：后者等上一次"完成"后再等 30 秒。
     * 对于推送重试这种快任务（一次扫表通常在毫秒级），两种效果几乎一致。</p>
     */
    @Scheduled(fixedRate = 30_000)
    public void retry() {
        log.debug("定时任务：开始扫描离线推送重试");
        pushService.retryFailedPushes(MAX_RETRY);
    }
}
