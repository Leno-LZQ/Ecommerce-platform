package com.ecommerce.promotionservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.promotionservice.dto.CouponTemplateVO;
import com.ecommerce.promotionservice.dto.CouponUserVO;
import com.ecommerce.promotionservice.entity.CouponTemplate;
import com.ecommerce.promotionservice.entity.CouponUser;
import com.ecommerce.promotionservice.mapper.CouponTemplateMapper;
import com.ecommerce.promotionservice.mapper.CouponUserMapper;
import com.ecommerce.promotionservice.config.PromotionProperties;
import com.ecommerce.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 优惠券服务——领券、发券、券查询、券状态机流转。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponTemplateMapper templateMapper;
    private final CouponUserMapper couponUserMapper;
    private final StringRedisTemplate redisTemplate;
    private final PromotionProperties props;

    private static final String COUPON_STOCK_PREFIX = "coupon:stock:";
    private static final String COUPON_RECV_PREFIX = "coupon:recv:";

    // ==================== C 端：可领券列表 ====================

    public List<CouponTemplateVO> getAvailableTemplates(Long userId) {
        // 查 status=1 且在领取窗内的模板
        List<CouponTemplate> templates = templateMapper.selectList(
            new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getStatus, 1)
                .le(CouponTemplate::getStartTime, LocalDateTime.now())
                .ge(CouponTemplate::getEndTime, LocalDateTime.now())
                .orderByDesc(CouponTemplate::getCreateTime));

        List<CouponTemplateVO> list = new ArrayList<>();
        for (CouponTemplate t : templates) {
            int remaining = t.getTotalQuantity() - (t.getIssuedQuantity() != null ? t.getIssuedQuantity() : 0);
            if (remaining <= 0) continue;

            int userReceived = couponUserMapper.countByUserAndTemplate(userId, t.getId());

            CouponTemplateVO vo = new CouponTemplateVO();
            vo.setId(t.getId());
            vo.setTemplateName(t.getTemplateName());
            vo.setCouponType(t.getCouponType());
            vo.setDiscountValue(t.getDiscountValue());
            vo.setThresholdAmount(t.getThresholdAmount());
            vo.setRemainingStock(remaining);
            vo.setPerUserLimit(t.getPerUserLimit());
            vo.setUserReceivedCount(userReceived);
            vo.setValidDays(t.getValidDays());
            vo.setStackable(t.getStackable() != null && t.getStackable() == 1);
            vo.setEndTime(t.getEndTime());
            list.add(vo);
        }
        return list;
    }

    // ==================== C 端：领取优惠券 ====================

    @Transactional
    public CouponUserVO receiveCoupon(Long userId, Long templateId) {
        CouponTemplate template = templateMapper.selectById(templateId);
        if (template == null || template.getStatus() == null || template.getStatus() != 1) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }
        if (template.getStartTime() != null && template.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PROMOTION_ENDED);
        }
        if (template.getEndTime() != null && template.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PROMOTION_ENDED);
        }

        // ① Redis 预检：库存
        String stockKey = COUPON_STOCK_PREFIX + templateId;
        String stockStr = redisTemplate.opsForValue().get(stockKey);
        int stock;
        if (stockStr == null) {
            // 兜底：模板创建时未初始化 Redis 库存（或 key 已过期），按 totalQuantity 初始化
            stock = template.getTotalQuantity();
            redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        } else {
            stock = Integer.parseInt(stockStr);
        }
        if (stock <= 0) {
            throw new BusinessException(ErrorCode.COUPON_STOCK_OUT);
        }

        // ② Redis 预检：每人限领
        String recvKey = COUPON_RECV_PREFIX + templateId + ":" + userId;
        String recvStr = redisTemplate.opsForValue().get(recvKey);
        int currentRecv = recvStr != null ? Integer.parseInt(recvStr) : 0;
        if (currentRecv >= template.getPerUserLimit()) {
            throw new BusinessException(ErrorCode.COUPON_LIMIT_EXCEEDED);
        }

        // ③ Redis 扣库存
        Long decrResult = redisTemplate.opsForValue().decrement(stockKey);
        if (decrResult != null && decrResult < 0) {
            // 回补
            redisTemplate.opsForValue().increment(stockKey);
            throw new BusinessException(ErrorCode.COUPON_STOCK_OUT);
        }

        // ④ Redis 增领取计数
        redisTemplate.opsForValue().increment(recvKey);
        long ttl = template.getEndTime() != null
            ? Duration.between(LocalDateTime.now(), template.getEndTime()).getSeconds()
            : 86400;
        if (ttl > 0) {
            redisTemplate.expire(recvKey, Duration.ofSeconds(ttl));
        }

        // ⑤ DB 原子计数（兜底）
        int rows = templateMapper.incrementIssuedQuantity(templateId);
        if (rows == 0) {
            // 回补 Redis
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForValue().decrement(recvKey);
            throw new BusinessException(ErrorCode.COUPON_STOCK_OUT);
        }

        // ⑥ 写 coupon_user
        CouponUser cu = new CouponUser();
        cu.setCouponCode(generateCouponCode());
        cu.setTemplateId(templateId);
        cu.setUserId(userId);
        cu.setStatus(0);
        cu.setExpireTime(LocalDateTime.now().plusDays(template.getValidDays()));
        couponUserMapper.insert(cu);

        return toUserVO(cu, template);
    }

    // ==================== C 端：我的券 ====================

    public List<CouponUserVO> getMyCoupons(Long userId, Integer status) {
        List<CouponUser> list;
        if (status != null) {
            list = couponUserMapper.selectByUserAndStatus(userId, status);
        } else {
            list = couponUserMapper.selectList(
                new LambdaQueryWrapper<CouponUser>()
                    .eq(CouponUser::getUserId, userId));
        }

        List<CouponUserVO> result = new ArrayList<>();
        for (CouponUser cu : list) {
            CouponTemplate t = templateMapper.selectById(cu.getTemplateId());
            if (t == null) continue;
            result.add(toUserVO(cu, t));
        }
        return result;
    }

    // ==================== 内部：按模板批量发券（新人券等） ====================

    @Transactional
    public void issueCouponByTemplate(Long userId, Long templateId) {
        CouponTemplate template = templateMapper.selectById(templateId);
        if (template == null) return;

        // 跳过领取窗校验，但仍走库存与限领
        int userReceived = couponUserMapper.countByUserAndTemplate(userId, templateId);
        if (userReceived >= template.getPerUserLimit()) return;

        // 简化版：直接 DB 计数 + 写 coupon_user（不发 Redis 库存，因为内部发券不走 C 端库存）
        int rows = templateMapper.incrementIssuedQuantity(templateId);
        if (rows == 0) return;

        CouponUser cu = new CouponUser();
        cu.setCouponCode(generateCouponCode());
        cu.setTemplateId(templateId);
        cu.setUserId(userId);
        cu.setStatus(0);
        cu.setExpireTime(LocalDateTime.now().plusDays(template.getValidDays()));
        couponUserMapper.insert(cu);

        log.info("自动发券成功 userId={} templateId={} code={}", userId, templateId, cu.getCouponCode());
    }

    // ==================== 私有辅助 ====================

    private CouponUserVO toUserVO(CouponUser cu, CouponTemplate t) {
        CouponUserVO vo = new CouponUserVO();
        vo.setCouponCode(cu.getCouponCode());
        vo.setTemplateId(t.getId());
        vo.setTemplateName(t.getTemplateName());
        vo.setCouponType(t.getCouponType());
        vo.setDiscountValue(t.getDiscountValue());
        vo.setThresholdAmount(t.getThresholdAmount());
        vo.setStatus(cu.getStatus());
        vo.setExpireTime(cu.getExpireTime());
        vo.setUseTime(cu.getUseTime());
        vo.setCreateTime(cu.getCreateTime());
        vo.setStackable(t.getStackable() != null && t.getStackable() == 1);
        if (cu.getExpireTime() != null) {
            vo.setRemainingDays(ChronoUnit.DAYS.between(LocalDateTime.now(), cu.getExpireTime()));
        }
        return vo;
    }

    /**
     * 生成券码：雪花 ID 转 32 位字符串。
     * 简化版：直接用 UUID 前 16 位 + 时间戳后 8 位。
     */
    private String generateCouponCode() {
        return "CN" + System.currentTimeMillis() % 100000000 + (int)(Math.random() * 10000);
    }
}
