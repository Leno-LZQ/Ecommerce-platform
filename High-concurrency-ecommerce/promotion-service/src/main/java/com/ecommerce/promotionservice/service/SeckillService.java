package com.ecommerce.promotionservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.promotionservice.dto.SeckillActivityVO;
import com.ecommerce.promotionservice.entity.PromotionScope;
import com.ecommerce.promotionservice.entity.SeckillActivity;
import com.ecommerce.promotionservice.mapper.PromotionScopeMapper;
import com.ecommerce.promotionservice.mapper.SeckillActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 秒杀活动服务——查询、库存预热、命中判定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillActivityMapper seckillMapper;
    private final PromotionScopeMapper scopeMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String SECKILL_STOCK_PREFIX = "seckill:stock:";

    // ==================== C 端查询 ====================

    public List<SeckillActivityVO> getSessions() {
        List<SeckillActivity> list = new ArrayList<>();
        list.addAll(seckillMapper.selectOngoing());
        list.addAll(seckillMapper.selectUpcoming(5));

        List<SeckillActivityVO> result = new ArrayList<>();
        for (SeckillActivity sa : list) {
            SeckillActivityVO vo = new SeckillActivityVO();
            vo.setId(sa.getId());
            vo.setProductId(sa.getProductId());
            vo.setSkuId(sa.getSkuId());
            vo.setSeckillPrice(sa.getSeckillPrice());
            vo.setSeckillStock(sa.getSeckillStock());
            vo.setPerUserLimit(sa.getPerUserLimit());
            vo.setStartTime(sa.getStartTime());
            vo.setEndTime(sa.getEndTime());
            vo.setStatus(sa.getStatus());
            if (sa.getStartTime() != null) {
                long sec = Duration.between(LocalDateTime.now(), sa.getStartTime()).getSeconds();
                vo.setCountdownSeconds(sec > 0 ? sec : 0);
            }
            result.add(vo);
        }
        return result;
    }

    public SeckillActivity getById(Long id) {
        return seckillMapper.selectById(id);
    }

    // ==================== 管理端 CRUD ====================

    @Transactional
    public Long create(SeckillActivity activity, List<PromotionScope> scopes) {
        activity.setStatus(0);
        seckillMapper.insert(activity);

        if (scopes != null) {
            for (PromotionScope scope : scopes) {
                scope.setTargetType("SECKILL");
                scope.setTargetId(activity.getId());
                scopeMapper.insert(scope);
            }
        }
        return activity.getId();
    }

    @Transactional
    public void update(Long id, SeckillActivity activity, List<PromotionScope> scopes) {
        SeckillActivity existing = seckillMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROMOTION_ENDED);
        }
        activity.setId(id);
        seckillMapper.updateById(activity);

        if (scopes != null) {
            scopeMapper.delete(
                new LambdaQueryWrapper<PromotionScope>()
                    .eq(PromotionScope::getTargetType, "SECKILL")
                    .eq(PromotionScope::getTargetId, id));
            for (PromotionScope scope : scopes) {
                scope.setTargetType("SECKILL");
                scope.setTargetId(id);
                scopeMapper.insert(scope);
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        SeckillActivity existing = seckillMapper.selectById(id);
        if (existing == null) return;
        if (existing.getStatus() != null && existing.getStatus() == 1) {
            throw new BusinessException(ErrorCode.PROMOTION_ENDED);
        }
        scopeMapper.delete(
            new LambdaQueryWrapper<PromotionScope>()
                .eq(PromotionScope::getTargetType, "SECKILL")
                .eq(PromotionScope::getTargetId, id));
        seckillMapper.deleteById(id);
    }

    // ==================== 库存预热 ====================

    /**
     * 将秒杀库存预热到 Redis（由 ActivityStatusJob 在开场前调用）。
     */
    public void preloadStock(Long seckillId) {
        SeckillActivity sa = seckillMapper.selectById(seckillId);
        if (sa == null || sa.getSeckillStock() == null) return;

        String key = SECKILL_STOCK_PREFIX + seckillId;
        redisTemplate.opsForValue().set(key, String.valueOf(sa.getSeckillStock()));

        // TTL 到秒杀结束
        if (sa.getEndTime() != null) {
            long ttl = Duration.between(LocalDateTime.now(), sa.getEndTime()).getSeconds();
            if (ttl > 0) {
                redisTemplate.expire(key, Duration.ofSeconds(ttl));
            }
        }
        log.info("秒杀库存预热 seckillId={}, stock={}", seckillId, sa.getSeckillStock());
    }

    /**
     * 清理已结束秒杀的 Redis 库存。
     */
    public void cleanupStock(Long seckillId) {
        redisTemplate.delete(SECKILL_STOCK_PREFIX + seckillId);
    }
}
