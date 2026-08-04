package com.ecommerce.csservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 客服限流服务
 *
 * <p>防滥用策略：</p>
 * <ul>
 *   <li>同一用户 1 小时内最多创建 5 个工单</li>
 *   <li>同一工单每天最多重新打开 3 次</li>
 * </ul>
 */
@Slf4j
@Service
public class TicketRateLimitService {

    private final StringRedisTemplate redisTemplate;

    public TicketRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试创建工单：1 小时 5 个
     */
    public boolean tryCreateTicket(Long userId) {
        String key = "cs:user:limit:" + userId;
        String countStr = redisTemplate.opsForValue().get(key);
        int count = countStr == null ? 0 : Integer.parseInt(countStr);
        if (count >= 5) {
            return false;
        }
        redisTemplate.opsForValue().increment(key);
        if (count == 0) {
            redisTemplate.expire(key, Duration.ofHours(1));
        }
        return true;
    }

    /**
     * 尝试重新打开工单：每天 3 次
     */
    public boolean tryReopenTicket(Long ticketId, Long userId) {
        String key = "cs:reopen:limit:" + ticketId + ":" + userId;
        String countStr = redisTemplate.opsForValue().get(key);
        int count = countStr == null ? 0 : Integer.parseInt(countStr);
        if (count >= 3) {
            return false;
        }
        redisTemplate.opsForValue().increment(key);
        if (count == 0) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        return true;
    }
}
