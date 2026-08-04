package com.ecommerce.promotionservice.controller;

import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.promotionservice.dto.admin.*;
import com.ecommerce.promotionservice.entity.*;
import com.ecommerce.promotionservice.mapper.*;
import com.ecommerce.promotionservice.service.ActivityService;
import com.ecommerce.promotionservice.service.SeckillService;
import com.ecommerce.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理端优惠引擎接口（admin-service BFF 代理 → 本服务）。
 */
@RestController
@RequestMapping("/api/admin/promotion")
@RequiredArgsConstructor
public class PromotionAdminController {

    private final ActivityService activityService;
    private final SeckillService seckillService;
    private final CouponTemplateMapper couponTemplateMapper;
    private final PromotionScopeMapper scopeMapper;
    private final PromotionRuleMapper ruleMapper;
    private final SeckillActivityMapper seckillActivityMapper;
    private final StringRedisTemplate redisTemplate;

    // ==================== 券模板 ====================

    @GetMapping("/coupon-templates")
    @PreAuthorize("hasAuthority('promotion:coupon:list')")
    public Result<List<CouponTemplate>> listCouponTemplates() {
        return Result.success(couponTemplateMapper.selectList(null));
    }

    @PostMapping("/coupon-templates")
    @PreAuthorize("hasAuthority('promotion:coupon:create')")
    public Result<Long> createCouponTemplate(@RequestBody CouponTemplateRequest req) {
        CouponTemplate t = new CouponTemplate();
        t.setTemplateName(req.getTemplateName());
        t.setCouponType(req.getCouponType());
        t.setDiscountValue(req.getDiscountValue());
        t.setThresholdAmount(req.getThresholdAmount());
        t.setTotalQuantity(req.getTotalQuantity());
        t.setIssuedQuantity(0);
        t.setUsedQuantity(0);
        t.setPerUserLimit(req.getPerUserLimit() != null ? req.getPerUserLimit() : 1);
        t.setValidDays(req.getValidDays());
        t.setStartTime(req.getStartTime());
        t.setEndTime(req.getEndTime());
        t.setStackable(req.getStackable() != null ? req.getStackable() : 0);
        t.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        couponTemplateMapper.insert(t);
        // 初始化 Redis 库存（领取时以 Redis 为准）
        redisTemplate.opsForValue().set("coupon:stock:" + t.getId(), String.valueOf(t.getTotalQuantity()));
        return Result.success(t.getId());
    }

    @PutMapping("/coupon-templates/{id}")
    @PreAuthorize("hasAuthority('promotion:coupon:update')")
    public Result<Void> updateCouponTemplate(@PathVariable Long id, @RequestBody CouponTemplateRequest req) {
        CouponTemplate t = couponTemplateMapper.selectById(id);
        if (t == null) throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        t.setTemplateName(req.getTemplateName());
        t.setCouponType(req.getCouponType());
        t.setDiscountValue(req.getDiscountValue());
        t.setThresholdAmount(req.getThresholdAmount());
        t.setTotalQuantity(req.getTotalQuantity());
        t.setPerUserLimit(req.getPerUserLimit());
        t.setValidDays(req.getValidDays());
        t.setStartTime(req.getStartTime());
        t.setEndTime(req.getEndTime());
        t.setStackable(req.getStackable());
        t.setStatus(req.getStatus());
        couponTemplateMapper.updateById(t);
        return Result.success();
    }

    @PostMapping("/coupon-templates/{id}/scopes")
    @PreAuthorize("hasAuthority('promotion:coupon:update')")
    public Result<Void> setCouponTemplateScopes(@PathVariable Long id, @RequestBody List<ScopeRequest> scopes) {
        scopeMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PromotionScope>()
                .eq(PromotionScope::getTargetType, "COUPON_TEMPLATE")
                .eq(PromotionScope::getTargetId, id));
        for (ScopeRequest sr : scopes) {
            PromotionScope s = new PromotionScope();
            s.setTargetType("COUPON_TEMPLATE");
            s.setTargetId(id);
            s.setScopeType(sr.getScopeType());
            s.setScopeId(sr.getScopeId());
            scopeMapper.insert(s);
        }
        return Result.success();
    }

    // ==================== 促销活动 ====================

    @GetMapping("/activities")
    @PreAuthorize("hasAuthority('promotion:activity:list')")
    public Result<List<PromotionActivity>> listActivities() {
        return Result.success(activityService.listAll());
    }

    @PostMapping("/activities")
    @PreAuthorize("hasAuthority('promotion:activity:create')")
    public Result<Long> createActivity(@RequestBody PromotionActivityRequest req) {
        PromotionActivity activity = new PromotionActivity();
        activity.setActivityName(req.getActivityName());
        activity.setActivityType(req.getActivityType());
        activity.setTotalBudget(req.getTotalBudget());
        activity.setRemainingBudget(req.getTotalBudget());
        activity.setUserDailyLimit(req.getUserDailyLimit());
        activity.setStartTime(req.getStartTime());
        activity.setEndTime(req.getEndTime());
        activity.setStatus(req.getStatus());

        List<PromotionRule> rules = null;
        if (req.getRules() != null) {
            rules = req.getRules().stream().map(r -> {
                PromotionRule rule = new PromotionRule();
                rule.setMinAmount(r.getMinAmount());
                rule.setMaxAmount(r.getMaxAmount());
                rule.setDiscountType(r.getDiscountType());
                rule.setDiscountValue(r.getDiscountValue());
                rule.setSortOrder(r.getSortOrder());
                return rule;
            }).collect(Collectors.toList());
        }

        List<PromotionScope> scopes = null;
        if (req.getScopes() != null) {
            scopes = req.getScopes().stream().map(s -> {
                PromotionScope scope = new PromotionScope();
                scope.setScopeType(s.getScopeType());
                scope.setScopeId(s.getScopeId());
                return scope;
            }).collect(Collectors.toList());
        }

        Long id = activityService.create(activity, rules, scopes);
        return Result.success(id);
    }

    @PutMapping("/activities/{id}")
    @PreAuthorize("hasAuthority('promotion:activity:update')")
    public Result<Void> updateActivity(@PathVariable Long id, @RequestBody PromotionActivityRequest req) {
        PromotionActivity activity = new PromotionActivity();
        activity.setActivityName(req.getActivityName());
        activity.setActivityType(req.getActivityType());
        activity.setTotalBudget(req.getTotalBudget());
        activity.setUserDailyLimit(req.getUserDailyLimit());
        activity.setStartTime(req.getStartTime());
        activity.setEndTime(req.getEndTime());

        List<PromotionRule> rules = null;
        if (req.getRules() != null) {
            rules = req.getRules().stream().map(r -> {
                PromotionRule rule = new PromotionRule();
                rule.setMinAmount(r.getMinAmount());
                rule.setMaxAmount(r.getMaxAmount());
                rule.setDiscountType(r.getDiscountType());
                rule.setDiscountValue(r.getDiscountValue());
                rule.setSortOrder(r.getSortOrder());
                return rule;
            }).collect(Collectors.toList());
        }

        List<PromotionScope> scopes = null;
        if (req.getScopes() != null) {
            scopes = req.getScopes().stream().map(s -> {
                PromotionScope scope = new PromotionScope();
                scope.setScopeType(s.getScopeType());
                scope.setScopeId(s.getScopeId());
                return scope;
            }).collect(Collectors.toList());
        }

        activityService.update(id, activity, rules, scopes);
        return Result.success();
    }

    // ==================== 秒杀 ====================

    @GetMapping("/seckill")
    @PreAuthorize("hasAuthority('promotion:seckill:list')")
    public Result<List<SeckillActivity>> listSeckill() {
        return Result.success(seckillActivityMapper.selectList(null));
    }

    @PostMapping("/seckill")
    @PreAuthorize("hasAuthority('promotion:seckill:create')")
    public Result<Long> createSeckill(@RequestBody SeckillActivityRequest req) {
        SeckillActivity sa = new SeckillActivity();
        sa.setActivityName(req.getActivityName());
        sa.setProductId(req.getProductId());
        sa.setSkuId(req.getSkuId());
        sa.setSeckillPrice(req.getSeckillPrice());
        sa.setSeckillStock(req.getSeckillStock());
        sa.setPerUserLimit(req.getPerUserLimit());
        sa.setStartTime(req.getStartTime());
        sa.setEndTime(req.getEndTime());
        sa.setStatus(req.getStatus());

        List<PromotionScope> scopes = null;
        if (req.getScopes() != null) {
            scopes = req.getScopes().stream().map(s -> {
                PromotionScope scope = new PromotionScope();
                scope.setScopeType(s.getScopeType());
                scope.setScopeId(s.getScopeId());
                return scope;
            }).collect(Collectors.toList());
        }

        Long id = seckillService.create(sa, scopes);
        return Result.success(id);
    }

    @PutMapping("/seckill/{id}")
    @PreAuthorize("hasAuthority('promotion:seckill:update')")
    public Result<Void> updateSeckill(@PathVariable Long id, @RequestBody SeckillActivityRequest req) {
        SeckillActivity sa = new SeckillActivity();
        sa.setActivityName(req.getActivityName());
        sa.setProductId(req.getProductId());
        sa.setSkuId(req.getSkuId());
        sa.setSeckillPrice(req.getSeckillPrice());
        sa.setSeckillStock(req.getSeckillStock());
        sa.setPerUserLimit(req.getPerUserLimit());
        sa.setStartTime(req.getStartTime());
        sa.setEndTime(req.getEndTime());

        List<PromotionScope> scopes = null;
        if (req.getScopes() != null) {
            scopes = req.getScopes().stream().map(s -> {
                PromotionScope scope = new PromotionScope();
                scope.setScopeType(s.getScopeType());
                scope.setScopeId(s.getScopeId());
                return scope;
            }).collect(Collectors.toList());
        }

        seckillService.update(id, sa, scopes);
        return Result.success();
    }
}
