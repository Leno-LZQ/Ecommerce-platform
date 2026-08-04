package com.ecommerce.promotionservice.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.promotionservice.entity.CouponTemplate;
import com.ecommerce.promotionservice.entity.CouponUser;
import com.ecommerce.promotionservice.entity.PromotionScope;
import com.ecommerce.promotionservice.mapper.CouponTemplateMapper;
import com.ecommerce.promotionservice.mapper.CouponUserMapper;
import com.ecommerce.promotionservice.mapper.PromotionScopeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Layer 3 — 优惠券匹配与试算。
 *
 * 查用户持有且 status=0 的券，逐张校验过期/模板状态/scope/门槛，
 * 计算候选优惠额，写入 ctx.couponCandidates 供 Layer 4 裁决。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
public class CouponMatchStep implements CalcStep {

    private final CouponUserMapper couponUserMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final PromotionScopeMapper scopeMapper;

    @Override
    public void apply(PromotionContext ctx) {
        List<String> codes = ctx.getCouponCodes();
        if (codes.isEmpty()) return;

        List<CouponUser> userCoupons = couponUserMapper.selectList(
            new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getUserId, ctx.getUserId())
                .eq(CouponUser::getStatus, 0)
                .in(CouponUser::getCouponCode, codes));
        if (userCoupons.isEmpty()) return;

        Set<Long> tids = userCoupons.stream()
            .map(CouponUser::getTemplateId).collect(Collectors.toSet());
        Map<Long, CouponTemplate> templateMap = couponTemplateMapper
            .selectBatchIds(tids).stream()
            .collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));

        Map<Long, List<PromotionScope>> scopeMap = scopeMapper
            .selectByTargets("COUPON_TEMPLATE", new ArrayList<>(tids))
            .stream()
            .collect(Collectors.groupingBy(PromotionScope::getTargetId));

        for (CouponUser uc : userCoupons) {
            tryMatch(ctx, uc, templateMap, scopeMap);
        }
    }

    private void tryMatch(PromotionContext ctx, CouponUser uc,
                          Map<Long, CouponTemplate> templateMap,
                          Map<Long, List<PromotionScope>> scopeMap) {

        CouponTemplate t = templateMap.get(uc.getTemplateId());
        if (t == null) return;

        if (uc.getExpireTime() != null && uc.getExpireTime().isBefore(LocalDateTime.now()))
            return;
        if (t.getStatus() == null || t.getStatus() != 1) return;

        List<PromotionScope> scopes = scopeMap.getOrDefault(t.getId(), List.of());
        List<CalcItemState> items = scopes.isEmpty()
            ? ctx.getActiveItems()
            : filterByScope(ctx.getActiveItems(), scopes);
        if (items.isEmpty()) return;

        BigDecimal subtotal = items.stream()
            .map(CalcItemState::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal threshold = t.getThresholdAmount() != null
            ? t.getThresholdAmount() : BigDecimal.ZERO;
        if (subtotal.compareTo(threshold) < 0) return;

        BigDecimal discount = calcDiscount(subtotal, t.getCouponType(), t.getDiscountValue());
        boolean stackable = t.getStackable() != null && t.getStackable() == 1;

        PromotionContext.CouponCandidate c = new PromotionContext.CouponCandidate(
            uc.getCouponCode(), t.getCouponType(), t.getDiscountValue(),
            threshold, stackable,subtotal, discount,items);
        ctx.getCouponCandidates().add(c);
    }

    // ===== 工具 =====

    private List<CalcItemState> filterByScope(List<CalcItemState> items,
                                              List<PromotionScope> scopes) {
        Set<Long> pids = new HashSet<>(), cids = new HashSet<>(), mids = new HashSet<>();
        for (PromotionScope s : scopes) {
            switch (s.getScopeType()) {
                case "PRODUCT" -> pids.add(s.getScopeId());
                case "CATEGORY" -> cids.add(s.getScopeId());
                case "MERCHANT" -> mids.add(s.getScopeId());
            }
        }
        return items.stream()
            .filter(i -> pids.contains(i.getProductId())
                || cids.contains(i.getCategoryId())
                || mids.contains(i.getMerchantId()))
            .collect(Collectors.toList());
    }

    private BigDecimal calcDiscount(BigDecimal subtotal, String type, BigDecimal val) {
        if (val == null) return BigDecimal.ZERO;
        return switch (type) {
            case "FULL_REDUCTION", "NO_THRESHOLD" -> {
                BigDecimal d = val;
                yield d.compareTo(subtotal) > 0 ? subtotal : d;
            }
            case "DISCOUNT" -> {
                BigDecimal ratio = BigDecimal.ONE.subtract(val);
                yield subtotal.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            }
            default -> BigDecimal.ZERO;
        };
    }
}
