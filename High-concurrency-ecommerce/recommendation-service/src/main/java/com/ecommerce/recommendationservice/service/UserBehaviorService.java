package com.ecommerce.recommendationservice.service;

import com.ecommerce.recommendationservice.dto.UserBehaviorRequest;
import com.ecommerce.recommendationservice.entity.UserBehavior;
import com.ecommerce.recommendationservice.entity.enums.BehaviorType;
import com.ecommerce.recommendationservice.mapper.UserBehaviorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 用户行为采集服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBehaviorService {

    private final UserBehaviorMapper userBehaviorMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String RECENT_KEY = "reco:user:%s:recent";
    private static final String PURCHASED_KEY = "reco:purchased:%s";
    private static final String EXCLUDE_KEY = "reco:exclude:%s";
    private static final long RECENT_TTL_DAYS = 7;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 记录行为：落库 + 更新 Redis 热缓存。
     */
    @Transactional(rollbackFor = Exception.class)
    public void record(UserBehaviorRequest request) {
        BehaviorType behaviorType = BehaviorType.of(request.getBehaviorType());
        if (behaviorType == null) {
            log.warn("未知的行为类型: {}", request.getBehaviorType());
            return;
        }

        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(request.getUserId());
        behavior.setProductId(request.getProductId());
        behavior.setBehaviorType(behaviorType);
        behavior.setSessionId(request.getSessionId());
        behavior.setSearchKeyword(request.getSearchKeyword());
        behavior.setDurationMs(request.getDurationMs());
        behavior.setCreatedAt(LocalDateTime.now());
        userBehaviorMapper.insert(behavior);

        updateRedisCache(request.getUserId(), request.getProductId(), behaviorType);
    }

    private void updateRedisCache(Long userId, Long productId, BehaviorType behaviorType) {
        try {
            String recentKey = String.format(RECENT_KEY, userId);
            double score = LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(8));
            redisTemplate.opsForZSet().add(recentKey, String.valueOf(productId), score);
            redisTemplate.expire(recentKey, RECENT_TTL_DAYS, TimeUnit.DAYS);

            if (behaviorType == BehaviorType.PURCHASE) {
                String purchasedKey = String.format(PURCHASED_KEY, userId);
                redisTemplate.opsForSet().add(purchasedKey, String.valueOf(productId));
            }
        } catch (Exception e) {
            log.warn("更新推荐 Redis 缓存失败, userId={}", userId, e);
        }
    }

    /**
     * 增加不感兴趣记录。
     */
    public void addExclude(Long userId, Long productId) {
        String key = String.format(EXCLUDE_KEY, userId);
        redisTemplate.opsForSet().add(key, String.valueOf(productId));
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
    }

}
