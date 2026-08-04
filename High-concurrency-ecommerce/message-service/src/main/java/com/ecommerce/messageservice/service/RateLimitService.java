package com.ecommerce.messageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * RateLimitService — Redis 滑动窗口限流器
 *
 * <p>用于防止单个用户在短时间内发送大量消息（消息轰炸）。</p>
 *
 * <h3>核心算法：滑动窗口</h3>
 * <p>用一个 Redis ZSet（有序集合）记录每次发消息的时间戳：</p>
 * <pre>
 *   Key:   msg:ratelimit:{conversationId}:{senderId}
 *   ZSet:  score=毫秒时间戳  member="1712345678901"
 * </pre>
 *
 * <p>每次发消息前执行：</p>
 * <ol>
 *   <li>用 {@code ZREMRANGEBYSCORE} 删除 60 秒前的旧记录</li>
 *   <li>用 {@code ZCOUNT} 统计最近 60 秒内有多少条</li>
 *   <li>{@code count >= 30} → 拒绝（返回 false）</li>
 *   <li>{@code count < 30} → 通过（返回 true），插入新记录</li>
 *   <li>设 key 过期时间 = 120 秒（兜底清理，防止内存泄漏）</li>
 * </ol>
 *
 * <h3>与其它限流方案的对比</h3>
 * <table>
 *   <tr><th>方案</th><th>优点</th><th>缺点</th></tr>
 *   <tr><td>固定窗口</td><td>简单</td><td>边界突发（第59秒和第61秒各30条=实际60条）</td></tr>
 *   <tr><td>滑动日志（ZSet）</td><td>精确，无边界问题</td><td>Redis 内存稍大</td></tr>
 *   <tr><td>令牌桶</td><td>允许突发</td><td>实现稍复杂</td></tr>
 * </table>
 * <p>本服务选 ZSet 滑动窗口：消息场景不需要突发，精确限流更重要。</p>
 *
 * <h3>为什么放到 Redis 而不是本地？</h3>
 * <p>message-service 可能有多个实例（水平扩容），用户可能连到不同实例。
 * 如果限流计数存在本地 JVM 内存里，两个实例各计数 15 条，加起来 30 条，
 * 每个都觉得自己没超限。用 Redis 统一计数才能保证全局准确。</p>
 */
@Slf4j
@Service
public class RateLimitService {

    /** 每个会话每分钟最多允许发送的消息数 */
    private static final int MAX_MESSAGES_PER_WINDOW = 30;

    /** 窗口长度：60 秒 */
    private static final long WINDOW_SECONDS = 60;

    /** Key 的 TTL：窗口的 2 倍，防止 key 残留 */
    private static final long KEY_TTL_SECONDS = WINDOW_SECONDS * 2;

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取一次发送许可
     *
     * <p>注意：这个方法不抛异常，只返回 true/false。
     * 由调用方（MessageService）决定如何处理超限情况。</p>
     *
     * @param conversationId 会话 ID（限流粒度：每个会话单独计数）
     * @param senderId       发送者 ID
     * @return true=允许发送，false=超限拒绝
     */
    public boolean tryAcquire(Long conversationId, Long senderId) {
        String key = buildKey(conversationId, senderId);
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_SECONDS * 1000;  // 60 秒前

        // ----------------------------------------------------------
        // 第①步：删除窗口外的旧记录
        // 等价 Redis 命令：ZREMRANGEBYSCORE key 0 {windowStart}
        // ----------------------------------------------------------
        redisTemplate.opsForZSet()
            .removeRangeByScore(key, 0, windowStart);

        // ----------------------------------------------------------
        // 第②步：统计窗口内的消息数
        // 等价 Redis 命令：ZCOUNT key {windowStart} +inf
        // ----------------------------------------------------------
        Long count = redisTemplate.opsForZSet()
            .count(key, windowStart, Double.POSITIVE_INFINITY);

        if (count != null && count >= MAX_MESSAGES_PER_WINDOW) {
            log.warn("消息限流触发: conversationId={}, senderId={}, count={}",
                conversationId, senderId, count);
            return false;  // 超限，拒绝
        }

        // ----------------------------------------------------------
        // 第③步：记录本次发送的时间戳
        // 等价 Redis 命令：ZADD key {now} {now}
        // member=now（字符串），score=now（数字）
        // ----------------------------------------------------------
        redisTemplate.opsForZSet()
            .add(key, String.valueOf(now), now);

        // ----------------------------------------------------------
        // 第④步：设置 key 过期时间（兜底清理）
        // 假如 N 多发完消息后删号了，这个 key 就没用了
        // 设置 TTL 可以让 Redis 在 120 秒后自动删除它
        // ----------------------------------------------------------
        redisTemplate.expire(key, Duration.ofSeconds(KEY_TTL_SECONDS));

        return true;
    }

    /**
     * 构建 Redis key
     * 格式：msg:ratelimit:{conversationId}:{senderId}
     * 示例：msg:ratelimit:1001:2001
     */
    private String buildKey(Long conversationId, Long senderId) {
        return "msg:ratelimit:" + conversationId + ":" + senderId;
    }

    /**
     * 获取当前窗口内的剩余配额（可选，供前端展示）
     *
     * @return 还能发几条
     */
    public long remainingQuota(Long conversationId, Long senderId) {
        String key = buildKey(conversationId, senderId);
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_SECONDS * 1000;

        Long count = redisTemplate.opsForZSet()
            .count(key, windowStart, Double.POSITIVE_INFINITY);

        long used = count != null ? count : 0;
        return Math.max(0, MAX_MESSAGES_PER_WINDOW - used);
    }
}
