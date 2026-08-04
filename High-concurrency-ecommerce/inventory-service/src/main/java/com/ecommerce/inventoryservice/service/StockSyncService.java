package com.ecommerce.inventoryservice.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis → MySQL 库存同步 + 超时预扣自动释放。
 *
 * 两个定时任务：
 *   1. syncStockToMySQL  — 每 5 分钟扫描 stock:* → 批量 UPDATE product_sku
 *   2. releaseExpired     — 每 1 分钟扫描 stock:reserved:* → 释放超时预扣
 *
 * 分布式安全：使用 Redis SETNX 分布式锁，多实例下只有一个执行。
 */
@Slf4j
@Service
public class StockSyncService {

    // ==================== 常量 ====================
    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final String RESERVED_KEY_PREFIX = "stock:reserved:";
    /** 预扣超时时间（分钟），超过此时间的 RESERVED 记录将被自动释放 */
    private static final int RESERVATION_EXPIRE_MINUTES = 30;
    /** TTL 低于此值认为即将过期，主动释放（给 Redis TTL 留缓冲） */
    private static final long TTL_RELEASE_THRESHOLD_SECONDS = 60;
    /** SCAN 每批数量 */
    private static final int SCAN_BATCH_SIZE = 200;

    // ==================== 依赖 ====================
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ==================== 配置 ====================
    @Value("${inventory.stock-sync.enabled:true}")
    private boolean syncEnabled;
    @Value("${inventory.stock-sync.batch-size:500}")
    private int updateBatchSize;

    // ==================== SQL ====================
    private static final String UPDATE_STOCK_SQL =
        "UPDATE product_sku SET stock = ?, update_time = NOW() WHERE id = ?";

    // ==================== 任务 1：Redis → MySQL 同步 ====================

    /**
     * 每 5 分钟执行：扫描 Redis 中所有 stock:* key → 批量 UPDATE MySQL product_sku.stock。
     * 用于 Redis 宕机后从 MySQL 恢复库存数据。
     */
    @Scheduled(fixedDelayString = "${inventory.stock-sync.interval-seconds:300}000",
        initialDelay = 60_000)  // 启动 1 分钟后开始，等 Redis 连接就绪
    public void syncStockToMySQL() {
        if (!syncEnabled) {
            log.debug("库存同步已禁用");
            return;
        }

        long start = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 分布式锁：多实例下只有一个执行
        if (!tryLock("lock:stock-sync", Duration.ofMinutes(10))) {
            log.debug("库存同步已被其他实例执行，跳过");
            return;
        }

        try {
            List<Object[]> batchArgs = new ArrayList<>();

            // SCAN 遍历所有 stock:* key（不用 KEYS，避免阻塞 Redis）
            scanKeys(STOCK_KEY_PREFIX + "*", key -> {
                Long skuId = parseSkuIdFromKey(key);
                if (skuId == null) return;

                String raw = redisTemplate.opsForValue().get(key);
                if (raw == null) return;

                try {
                    int stock = Integer.parseInt(raw);
                    batchArgs.add(new Object[]{stock, skuId});
                } catch (NumberFormatException e) {
                    log.error("库存值非数字: key={}, value={}", key, raw);
                    failCount.incrementAndGet();
                    return;
                }

                // 攒够一批就批量写
                if (batchArgs.size() >= updateBatchSize) {
                    int[] results = jdbcTemplate.batchUpdate(UPDATE_STOCK_SQL, batchArgs);
                    successCount.addAndGet(countAffected(results));
                    batchArgs.clear();
                }
            });

            // 最后一批
            if (!batchArgs.isEmpty()) {
                int[] results = jdbcTemplate.batchUpdate(UPDATE_STOCK_SQL, batchArgs);
                successCount.addAndGet(countAffected(results));
            }

        } catch (DataAccessException e) {
            log.error("MySQL 连接异常，跳过本次同步", e);
            return;
        } catch (Exception e) {
            log.error("库存同步异常", e);
        } finally {
            unlock("lock:stock-sync");
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("库存同步完成: success={}, fail={}, elapsed={}ms",
            successCount.get(), failCount.get(), elapsed);
    }

    // ==================== 任务 1.5：MySQL → Redis 库存初始化 ====================

    /**
     * 每 5 分钟执行：扫描 product_sku（DB），为缺失的 stock:{skuId} 补建 Redis 库存。
     * 仅 setIfAbsent，不覆盖线上实时值；用于 Redis 宕机恢复 / 首次启动自动建库存，
     * 避免依赖人工 SET。
     */
    @Scheduled(fixedDelayString = "${inventory.stock-init.interval-seconds:300}000",
        initialDelay = 30_000)
    public void initStockFromMySQL() {
        if (!syncEnabled) {
            log.debug("库存初始化已禁用");
            return;
        }
        if (!tryLock("lock:stock-init", Duration.ofMinutes(10))) {
            log.debug("库存初始化已被其他实例执行，跳过");
            return;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, stock FROM product_sku");
            AtomicInteger initialized = new AtomicInteger(0);
            for (Map<String, Object> row : rows) {
                Number id = (Number) row.get("id");
                Number stock = (Number) row.get("stock");
                if (id == null || stock == null) continue;
                Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                    STOCK_KEY_PREFIX + id.longValue(), String.valueOf(stock.intValue()));
                if (Boolean.TRUE.equals(ok)) {
                    initialized.incrementAndGet();
                }
            }
            log.info("库存初始化完成: initialized={}, total={}", initialized.get(), rows.size());
        } catch (Exception e) {
            log.error("库存初始化异常", e);
        } finally {
            unlock("lock:stock-init");
        }
    }

    /** 手动触发一次库存初始化（供管理/测试），返回本次补建的 key 数量 */
    public int initStockNow() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, stock FROM product_sku");
        AtomicInteger initialized = new AtomicInteger(0);
        for (Map<String, Object> row : rows) {
            Number id = (Number) row.get("id");
            Number stock = (Number) row.get("stock");
            if (id == null || stock == null) continue;
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                STOCK_KEY_PREFIX + id.longValue(), String.valueOf(stock.intValue()));
            if (Boolean.TRUE.equals(ok)) initialized.incrementAndGet();
        }
        log.info("库存手动初始化完成: initialized={}, total={}", initialized.get(), rows.size());
        return initialized.get();
    }

    // ==================== 任务 2：超时预扣释放 ====================

    /**
     * 每 1 分钟执行：扫描 stock:reserved:* → 释放超时未确认的预扣。
     *
     * 判定超时策略（双重检查）：
     *   1. Redis TTL < TTL_RELEASE_THRESHOLD（60s）→ 马上过期，释放
     *   2. TTL = -1（无过期时间，异常）→ 释放
     *
     * 释放逻辑：读 Hash 中 SKU-qty 明细 → INCRBY 回补 → 删 Hash。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void releaseExpiredReservations() {
        if (!syncEnabled) {
            return;
        }

        if (!tryLock("lock:release-expired", Duration.ofMinutes(2))) {
            log.debug("超时释放已被其他实例执行，跳过");
            return;
        }

        AtomicInteger releasedCount = new AtomicInteger(0);
        AtomicInteger orphanCount = new AtomicInteger(0);

        try {
            scanKeys(RESERVED_KEY_PREFIX + "*", key -> {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                String orderNo = key.substring(RESERVED_KEY_PREFIX.length());

                // 确定是否需要释放
                boolean shouldRelease;
                if (ttl == null || ttl < 0) {
                    // key 不存在 (ttl=-2) 或没有 TTL (ttl=-1)
                    if (ttl != null && ttl == -2L) {
                        return;  // 已被其他流程删除
                    }
                    shouldRelease = true;  // ttl=-1 表示异常：有 key 但无过期时间
                } else {
                    shouldRelease = ttl <= TTL_RELEASE_THRESHOLD_SECONDS;
                }

                if (!shouldRelease) return;

                // 读取预扣明细并释放
                Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
                if (hash.isEmpty()) {
                    redisTemplate.delete(key);
                    return;
                }

                String status = (String) hash.get("status");
                if ("PENDING".equals(status)) {
                    // PENDING 状态：Lua 可能没执行，直接清理
                    log.warn("清理孤儿预扣(PENDING): orderNo={}", orderNo);
                    redisTemplate.delete(key);
                    orphanCount.incrementAndGet();
                    return;
                }

                // RESERVED 状态：正常释放
                int itemCount = 0;
                for (Map.Entry<Object, Object> entry : hash.entrySet()) {
                    String field = (String) entry.getKey();
                    if ("status".equals(field)) continue;
                    try {
                        String skuIdStr = field;
                        int qty = Integer.parseInt((String) entry.getValue());
                        redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + skuIdStr, qty);
                        itemCount++;
                    } catch (NumberFormatException e) {
                        log.error("预扣明细数据异常: orderNo={}, field={}", orderNo, field, e);
                    }
                }
                redisTemplate.delete(key);
                releasedCount.addAndGet(itemCount);
                log.info("自动释放超时预扣: orderNo={}, items={}", orderNo, itemCount);
            });
        } catch (Exception e) {
            log.error("超时释放扫描异常", e);
        } finally {
            unlock("lock:release-expired");
        }

        log.info("超时释放完成: releasedSkus={}, orphanCount={}",
            releasedCount.get(), orphanCount.get());
    }

    // ==================== 工具方法 ====================

    /** SCAN 方式遍历 key，避免 KEYS 阻塞 Redis */
    private void scanKeys(String pattern, java.util.function.Consumer<String> consumer) {
        ScanOptions options = ScanOptions.scanOptions()
            .match(pattern)
            .count(SCAN_BATCH_SIZE)
            .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(consumer);
        } catch (Exception e) {
            log.error("SCAN 异常: pattern={}", pattern, e);
        }
    }

    /** 从 "stock:10001" 解析出 10001 */
    private Long parseSkuIdFromKey(String key) {
        try {
            String idPart = key.substring(STOCK_KEY_PREFIX.length());
            return Long.valueOf(idPart);
        } catch (Exception e) {
            log.warn("无法解析 SKU ID: key={}", key);
            return null;
        }
    }

    /** 统计 batchUpdate 影响行数 */
    private int countAffected(int[] results) {
        int count = 0;
        for (int r : results) {
            if (r > 0) count++;
        }
        return count;
    }

    // ==================== 简版 Redis 分布式锁 ====================

    private boolean tryLock(String lockKey, Duration expire) {
        Boolean ok = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", expire);
        return Boolean.TRUE.equals(ok);
    }

    private void unlock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
