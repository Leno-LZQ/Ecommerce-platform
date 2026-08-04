package com.ecommerce.productservice.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.ecommerce.productservice.dto.ProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ProductCacheService {

    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private Cache<Long, ProductResponse> caffeineCache;

    private static final String REDIS_KEY_PREFIX = "product:";
    private static final int REDIS_TTL_BASE = 30;              // 秒
    private static final int REDIS_TTL_OFFSET = 10;            // ±10s
    private final Random random = new Random();

    // ==================== get ====================
    public ProductResponse get(Long productId){

        // Step 1：查 Caffeine L1 缓存
        ProductResponse cached = caffeineCache.getIfPresent(productId);
        if (cached != null) {
            log.debug("Caffeine L1 命中: productId={}", productId);
            return cached;
        }
        // Step 2：查 Redis L2 缓存
        String redisKey = REDIS_KEY_PREFIX + productId;
        ProductResponse redisCached = (ProductResponse) redisTemplate.opsForValue().get(redisKey);
        if (redisCached != null) {
            // 回种 Caffeine
            caffeineCache.put(productId, redisCached);
            log.debug("Redis L2 命中: productId={}", productId);
            return redisCached;
        }

        log.debug("缓存未命中: productId={}", productId);
        return null;  // 返回 null，调用方查 MySQL
    }

    // ==================== put ====================
    public void put(Long productId, ProductResponse vo) {
        // 写入 Redis（TTL 随机化防雪崩）
        String redisKey = REDIS_KEY_PREFIX + productId;
        int ttl = REDIS_TTL_BASE + random.nextInt(REDIS_TTL_OFFSET * 2 + 1) - REDIS_TTL_OFFSET;
        redisTemplate.opsForValue().set(redisKey, vo, ttl, TimeUnit.SECONDS);

        // 写入 Caffeine（固定 5s TTL）
        caffeineCache.put(productId, vo);
        log.debug("缓存写入: productId={}, redisTtl={}s", productId, ttl);
    }

    // ==================== evict ====================
    public void evict(Long productId) {
        // 删 Redis
        redisTemplate.delete(REDIS_KEY_PREFIX + productId);
        // 删 Caffeine
        caffeineCache.invalidate(productId);
        log.debug("缓存清除: productId={}", productId);
    }

}
