package com.ecommerce.inventoryservice.service;

import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.dto.inventory.FailedItem;
import com.ecommerce.dto.inventory.ReserveItem;
import com.ecommerce.dto.inventory.ReserveRequest;
import com.ecommerce.dto.inventory.ReserveResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class InventoryService {

    // ==================== 常量 ====================
    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final String RESERVED_KEY_PREFIX = "stock:reserved:";
    private static final int STOCK_UNKNOWN = -1;
    /** 预扣 Hash 的过期时间（分钟），超时后定时任务自动释放 */
    private static final int RESERVATION_TTL_MINUTES = 30;

    // ==================== Redis Key 工具 ====================
    private static String stockKey(Long skuId) { return STOCK_KEY_PREFIX + skuId; }
    private static String reservedKey(String orderNo) { return RESERVED_KEY_PREFIX + orderNo; }

    // ==================== 依赖注入 ====================
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private RedisScript<List> batchReserveScript;
    @Autowired private RedisScript<Long> releaseStockScript;
    @Autowired(required = false) private MeterRegistry meterRegistry;

    // ==================== 配置 ====================
    @Value("${inventory.cache.ttl-seconds:3}")
    private int cacheTtlSeconds;
    @Value("${inventory.cache.max-size:20000}")
    private int cacheMaxSize;
    @Value("${inventory.low-stock-threshold:10}")
    private int lowStockThreshold;

    // ==================== 本地缓存 ====================
    private Cache<Long, Integer> localStockCache;

    // ==================== 监控指标 ====================
    private Counter cacheHitCounter;
    private Counter cacheMissCounter;
    private Counter redisFailureCounter;
    private Counter reserveSuccessCounter;
    private Counter reserveRejectCounter;       // 库存不足
    private Counter reserveFailCounter;         // 系统异常
    private Counter confirmCounter;
    private Counter releaseCounter;
    private Counter rollbackCounter;

    @PostConstruct
    public void init() {
        this.localStockCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
            .maximumSize(cacheMaxSize)
            .recordStats()
            .build();

        if (meterRegistry != null) {
            cacheHitCounter    = Counter.builder("inventory.cache.hit")
                .description("本地缓存命中").register(meterRegistry);
            cacheMissCounter   = Counter.builder("inventory.cache.miss")
                .description("本地缓存未命中").register(meterRegistry);
            redisFailureCounter = Counter.builder("inventory.redis.failure")
                .description("Redis 查询失败").register(meterRegistry);
            reserveSuccessCounter = Counter.builder("inventory.reserve.success")
                .description("预扣成功").register(meterRegistry);
            reserveRejectCounter  = Counter.builder("inventory.reserve.reject")
                .description("库存不足拒绝").register(meterRegistry);
            reserveFailCounter    = Counter.builder("inventory.reserve.fail")
                .description("预扣系统异常").register(meterRegistry);
            confirmCounter   = Counter.builder("inventory.confirm")
                .description("确认扣减").register(meterRegistry);
            releaseCounter   = Counter.builder("inventory.release")
                .description("释放库存").register(meterRegistry);
            rollbackCounter  = Counter.builder("inventory.rollback")
                .description("回滚库存").register(meterRegistry);
        }
    }

    // ================================================================
    // 1. 单 SKU 查询
    // ================================================================

    /**
     * 查询单个 SKU 的当前可售库存。
     * @return 库存数量；0=售罄；-1=SKU不存在或查询异常
     */
    public int getStock(Long skuId) {
        if (skuId == null) {
            log.warn("getStock 收到 null skuId");
            return STOCK_UNKNOWN;
        }

        Integer cached = localStockCache.getIfPresent(skuId);
        if (cached != null) {
            increment(cacheHitCounter);
            return cached;
        }
        increment(cacheMissCounter);

        int stock = loadStockFromRedis(skuId);
        if (stock != STOCK_UNKNOWN) {
            localStockCache.put(skuId, stock);
        }
        return stock;
    }

    // ================================================================
    // 2. 批量查询
    // ================================================================

    /**
     * 批量查询库存。pipeline 一次拉取，避免 N 次网络往返。
     * @return skuId → stock；不存在的 SKU 值为 -1
     */
    public Map<Long, Integer> batchGetStock(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Integer> result = new LinkedHashMap<>(skuIds.size());

        // L1：Caffeine
        List<Long> missedIds = new ArrayList<>();
        for (Long skuId : skuIds) {
            Integer cached = localStockCache.getIfPresent(skuId);
            if (cached != null) {
                result.put(skuId, cached);
                increment(cacheHitCounter);
            } else {
                missedIds.add(skuId);
                increment(cacheMissCounter);
            }
        }

        // L2：Redis pipeline
        if (!missedIds.isEmpty()) {
            List<String> keys = missedIds.stream().map(InventoryService::stockKey).toList();
            try {
                List<String> values = redisTemplate.opsForValue().multiGet(keys);
                if (values != null) {
                    for (int i = 0; i < missedIds.size(); i++) {
                        Long skuId = missedIds.get(i);
                        int stock = parseStock(skuId, values.get(i));
                        result.put(skuId, stock);
                        if (stock != STOCK_UNKNOWN) {
                            localStockCache.put(skuId, stock);
                        }
                    }
                }
            } catch (RedisConnectionFailureException e) {
                log.error("批量查库存 Redis 连接失败, size={}", missedIds.size(), e);
                increment(redisFailureCounter);
                for (Long skuId : missedIds) {
                    result.put(skuId, STOCK_UNKNOWN);
                }
            }
        }
        return result;
    }

    // ================================================================
    // 3. 预扣库存（核心写操作）
    // ================================================================

    /**
     * 预扣库存。Lua 原子执行 + SETNX 幂等锁。
     *
     * 时序：
     *   1. SETNX 占坑（防 order-service 重试重复扣）
     *   2. Lua 原子扣减
     *   3. 成功 → 写 Hash 记录明细；失败 → 删占坑
     */
    public ReserveResult reserve(ReserveRequest request) {
        // --- 参数校验 ---
        if (request == null || request.getOrderNo() == null
            || request.getItems() == null || request.getItems().isEmpty()) {
            log.warn("reserve 无效请求: {}", request);
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }
        for (ReserveItem item : request.getItems()) {
            if (item.getSkuId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                log.warn("reserve 非法条目: orderNo={}, item={}", request.getOrderNo(), item);
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
            }
        }

        String orderNo = request.getOrderNo();
        String reservationKey = reservedKey(orderNo);
        String reservationLockKey = "reserved:lock:" + orderNo; // 幂等锁 key（与明细 Hash 分离，避免 WRONGTYPE）

        // --- 幂等锁：SETNX 占坑 ---
        Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(reservationLockKey, "PENDING", Duration.ofMinutes(RESERVATION_TTL_MINUTES));
        if (Boolean.FALSE.equals(locked)) {
            Object existing = redisTemplate.opsForHash().get(reservationKey, "status");
            if ("RESERVED".equals(existing)) {
                log.info("订单已预扣(重试幂等): orderNo={}", orderNo);
                return new ReserveResult(true, orderNo, List.of());
            }
            log.warn("订单处理中，拒绝重入: orderNo={}", orderNo);
            throw new BusinessException(ErrorCode.ORDER_DUPLICATE);
        }

        // --- 构建 Lua 参数 ---
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        for (ReserveItem item : request.getItems()) {
            keys.add(stockKey(item.getSkuId()));
            args.add(String.valueOf(item.getQuantity()));
        }

        // --- 执行 Lua ---
        List<Long> luaResult;
        try {
            //noinspection unchecked
            luaResult = (List<Long>) redisTemplate.execute(batchReserveScript, keys, args.toArray());
        } catch (Exception e) {
            log.error("Lua 执行异常, orderNo={}", orderNo, e);
            redisTemplate.delete(reservationLockKey);
            increment(reserveFailCounter);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }

        if (luaResult == null || luaResult.isEmpty()) {
            redisTemplate.delete(reservationLockKey);
            log.error("Lua 返回空, orderNo={}", orderNo);
            increment(reserveFailCounter);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }

        // --- Lua 失败：库存不足 → 释放占坑 ---
        if (luaResult.get(0) == 0L) {
            redisTemplate.delete(reservationLockKey);
            int failedIndex = luaResult.get(1).intValue() - 1; // Lua 1-based
            ReserveItem failedItem = request.getItems().get(failedIndex);
            long available = luaResult.get(2);
            long required  = luaResult.get(3);

            log.info("库存不足: orderNo={}, skuId={}, need={}, have={}",
                orderNo, failedItem.getSkuId(), required, available);
            increment(reserveRejectCounter);
            return new ReserveResult(false, orderNo,
                List.of(new FailedItem(failedItem.getSkuId(), (int) required, (int) available)));
        }

        // --- Lua 成功 → 写入预扣明细 Hash ---
        Map<String, String> hashData = new LinkedHashMap<>();
        hashData.put("status", "RESERVED");
        for (ReserveItem item : request.getItems()) {
            hashData.put(String.valueOf(item.getSkuId()), String.valueOf(item.getQuantity()));
        }
        redisTemplate.opsForHash().putAll(reservationKey, hashData);
        redisTemplate.expire(reservationKey, Duration.ofMinutes(RESERVATION_TTL_MINUTES));
        redisTemplate.delete(reservationLockKey); // 预扣成功，释放幂等锁

        // --- 清除本地缓存 + 低库存检查 ---
        for (ReserveItem item : request.getItems()) {
            localStockCache.invalidate(item.getSkuId());
            checkLowStock(item.getSkuId());
        }

        log.info("预扣成功: orderNo={}, items={}", orderNo,
            request.getItems().stream()
                .map(i -> i.getSkuId() + "x" + i.getQuantity()).toList());
        increment(reserveSuccessCounter);
        return new ReserveResult(true, orderNo, List.of());
    }

    // ================================================================
    // 4. 确认扣减（支付成功）
    // ================================================================

    /**
     * 确认扣减。支付成功后调用。
     * 库存已在 reserve 阶段扣减，confirm 只需清理预扣记录。
     */
    public void confirm(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("confirm 收到空 orderNo");
            return;
        }
        String key = reservedKey(orderNo);

        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
        if (hash.isEmpty()) {
            log.warn("预扣记录不存在(可能已过期或已确认): orderNo={}", orderNo);
            return;
        }

        // 清除 SKU 缓存（虽然库存没变，但保持缓存一致性）
        for (Object skuObj : hash.keySet()) {
            if (!"status".equals(skuObj)) {
                try {
                    localStockCache.invalidate(Long.valueOf((String) skuObj));
                } catch (NumberFormatException ignored) {
                    // status 字段不是数字，跳过
                }
            }
        }

        redisTemplate.delete(key);
        log.info("确认扣减成功: orderNo={}", orderNo);
        increment(confirmCounter);
    }

    // ================================================================
    // 5. 释放库存（超时取消 / 用户取消）
    // ================================================================

    /**
     * 释放库存。从预扣记录读取明细，逐 SKU 回补。
     */
    public void release(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("release 收到空 orderNo");
            return;
        }
        String key = reservedKey(orderNo);

        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
        if (hash.isEmpty()) {
            log.warn("预扣记录不存在(可能已过期或已释放): orderNo={}", orderNo);
            return;
        }

        String status = (String) hash.get("status");
        if (!"RESERVED".equals(status)) {
            log.warn("预扣状态异常: orderNo={}, status={}", orderNo, status);
            redisTemplate.delete(key);
            return;
        }

        // 逐 SKU 回补库存
        int releasedCount = 0;
        for (Map.Entry<Object, Object> entry : hash.entrySet()) {
            String field = (String) entry.getKey();
            if ("status".equals(field)) continue;

            try {
                Long skuId = Long.valueOf(field);
                int qty = Integer.parseInt((String) entry.getValue());

                redisTemplate.opsForValue().increment(stockKey(skuId), qty);
                localStockCache.invalidate(skuId);
                releasedCount++;

                log.info("释放库存: orderNo={}, skuId={}, qty={}", orderNo, skuId, qty);
            } catch (NumberFormatException e) {
                log.error("预扣记录数据异常: orderNo={}, field={}, value={}",
                    orderNo, field, entry.getValue(), e);
            }
        }

        redisTemplate.delete(key);
        log.info("释放完成: orderNo={}, releasedSkus={}", orderNo, releasedCount);
        increment(releaseCounter);
    }

    // ================================================================
    // 6. 回滚库存（退款）
    // ================================================================

    /**
     * 回滚库存。退款场景，语义同 release（回补库存）。
     */
    public void rollback(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("rollback 收到空 orderNo");
            return;
        }
        // 语义完全相同，复用 release
        log.info("退款回滚: orderNo={}", orderNo);
        release(orderNo);
        increment(rollbackCounter);
    }

    // ================================================================
    // 内部方法
    // ================================================================

    private int loadStockFromRedis(Long skuId) {
        try {
            return parseStock(skuId, redisTemplate.opsForValue().get(stockKey(skuId)));
        } catch (RedisConnectionFailureException e) {
            log.error("Redis 连接异常, skuId={}", skuId, e);
            increment(redisFailureCounter);
            return STOCK_UNKNOWN;
        }
    }

    private int parseStock(Long skuId, String raw) {
        if (raw == null) return STOCK_UNKNOWN;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            log.error("库存数据损坏, skuId={}, value={}", skuId, raw, e);
            return STOCK_UNKNOWN;
        }
    }

    /** 低库存告警 */
    private void checkLowStock(Long skuId) {
        try {
            String raw = redisTemplate.opsForValue().get(stockKey(skuId));
            if (raw != null) {
                int remaining = Integer.parseInt(raw);
                if (remaining <= lowStockThreshold) {
                    log.warn("低库存告警: skuId={}, remaining={}, threshold={}",
                        skuId, remaining, lowStockThreshold);
                }
            }
        } catch (Exception e) {
            log.error("低库存检查异常, skuId={}", skuId, e);
        }
    }

    private void increment(Counter counter) {
        if (counter != null) counter.increment();
    }
}
