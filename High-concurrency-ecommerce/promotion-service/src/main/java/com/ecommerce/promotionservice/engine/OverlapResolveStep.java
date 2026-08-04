package com.ecommerce.promotionservice.engine;

import com.ecommerce.promotionservice.mapper.PromotionActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Layer 4 — 叠加互斥裁决。
 *
 * 输入: ctx.activityCandidates + ctx.couponCandidates
 * 输出: ctx.totalDiscount / ctx.trace / ctx.budgetDeductions / ctx.dailyIncrements
 *
 * 裁决规则见文档 §11.6.3 互斥矩阵：
 * - 不同商家活动独立；同组互斥取最大
 * - N_M 独占商品
 * - 活动 + 券可叠加（券门槛按活动后金额重算）
 * - 平台券互斥取最优（stackable=1 除外）
 * - 平台券 + 商家券可叠加
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(4)
public class OverlapResolveStep implements CalcStep {

    private static final String TYPE_N_M = "N_M";
    private final PromotionActivityMapper activityMapper;

    @Override
    public void apply(PromotionContext ctx) {
        List<PromotionContext.ActivityCandidate> winningActs = resolveActivities(ctx);
        List<PromotionContext.CouponCandidate> winningCoupons = resolveCoupons(ctx, winningActs);

        ctx.getWinningActivities().addAll(winningActs);
        ctx.getWinningCoupons().addAll(winningCoupons);

        BigDecimal total = BigDecimal.ZERO;
        for (var ac : winningActs) {
            total = total.add(ac.getDiscountAmount());
            ctx.getTrace().add(new CalcItemState.HitEntry(
                "L4", "ACTIVITY", ac.getActivityId(), ac.getDiscountAmount()));
            if (ctx.getMode() == CalcMode.LOCK) {
                ctx.getBudgetDeductions().put(ac.getActivityId(),
                    ac.getDiscountAmount().multiply(BigDecimal.valueOf(100)).longValue());
                ctx.getDailyIncrements().put(ac.getActivityId(), 1L);
            }
        }
        for (var cc : winningCoupons) {
            total = total.add(cc.getCalculatedDiscount());
            ctx.getTrace().add(new CalcItemState.HitEntry(
                "L4", "COUPON", 0L, cc.getCalculatedDiscount()));
        }
        ctx.setTotalDiscount(total);

    }

    // ===== 活动裁决 =====

    private List<PromotionContext.ActivityCandidate> resolveActivities(PromotionContext ctx) {
        var candidates = ctx.getActivityCandidates();
        if (candidates.isEmpty()) return List.of();

        List<PromotionContext.ActivityCandidate> all = new ArrayList<>(candidates.values());
        List<PromotionContext.ActivityCandidate> n_mList = new ArrayList<>();
        List<PromotionContext.ActivityCandidate> normal = new ArrayList<>();

        for (var ac : all) {
            (TYPE_N_M.equals(getActivityType(ac.getActivityId())) ? n_mList : normal).add(ac);
        }

        Set<CalcItemState> occupied = new HashSet<>();
        n_mList.forEach(ac -> occupied.addAll(ac.getMatchedItems()));
        List<PromotionContext.ActivityCandidate> winners = new ArrayList<>(n_mList);

        normal.sort(Comparator.comparing(
            PromotionContext.ActivityCandidate::getDiscountAmount).reversed());
        for (var ac : normal) {
            if (ac.getMatchedItems().stream().noneMatch(occupied::contains)) {
                winners.add(ac);
                occupied.addAll(ac.getMatchedItems());
            }
        }
        return winners;
    }

    private String getActivityType(Long activityId) {
        var a = activityMapper.selectById(activityId);
        return a != null ? a.getActivityType() : null;
    }

    // ===== 券裁决 =====

    private List<PromotionContext.CouponCandidate> resolveCoupons(
        PromotionContext ctx,
        List<PromotionContext.ActivityCandidate> winningActs) {

        var candidates = ctx.getCouponCandidates();
        if (candidates.isEmpty()) return List.of();

        List<Recalculated> recalculated = candidates.stream()
            .map(cc -> recalc(cc, winningActs))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (recalculated.isEmpty()) return List.of();

        // 分类
        List<Recalculated> platform = new ArrayList<>();
        Map<Long, List<Recalculated>> byMerchant = new LinkedHashMap<>();
        for (var rc : recalculated) {
            Long mid = singleMerchantId(rc.original);
            if (mid == null) platform.add(rc);
            else byMerchant.computeIfAbsent(mid, k -> new ArrayList<>()).add(rc);
        }

        Comparator<Recalculated> cmp = Comparator.comparing(
            (Recalculated r) -> r.effectiveDiscount).reversed();
        platform.sort(cmp);
        byMerchant.values().forEach(l -> l.sort(cmp));

        List<PromotionContext.CouponCandidate> winners = new ArrayList<>();
        for (var rc : platform) {
            if (winners.isEmpty() || rc.original.isStackable()) winners.add(rc.original);
        }
        for (var list : byMerchant.values()) {
            if (!list.isEmpty()) winners.add(list.get(0).original);
        }

        // 回写重算金额
        for (var rc : recalculated) {
            rc.original.setApplicableSubtotal(rc.effectiveSubtotal);
            rc.original.setCalculatedDiscount(rc.effectiveDiscount);
        }
        return winners;
    }

    private Recalculated recalc(PromotionContext.CouponCandidate cc,
                                List<PromotionContext.ActivityCandidate> winningActs) {
        BigDecimal effective = BigDecimal.ZERO;
        for (var item : cc.getApplicableItems()) {
            BigDecimal s = item.getSubtotal();
            for (var ac : winningActs) {
                if (ac.getMatchedItems().contains(item)) {
                    BigDecimal ratio = s.divide(ac.getGroupSubtotal(), 6, RoundingMode.HALF_UP);
                    s = s.subtract(ratio.multiply(ac.getDiscountAmount()));
                    if (s.compareTo(BigDecimal.ZERO) < 0) s = BigDecimal.ZERO;
                }
            }
            effective = effective.add(s);
        }
        if (effective.compareTo(cc.getThresholdAmount()) < 0) return null;
        BigDecimal d = calcCouponDiscount(effective, cc.getCouponType(), cc.getDiscountValue());
        return new Recalculated(cc, effective, d);
    }

    private Long singleMerchantId(PromotionContext.CouponCandidate cc) {
        Set<Long> ids = cc.getApplicableItems().stream()
            .map(CalcItemState::getMerchantId).collect(Collectors.toSet());
        return ids.size() == 1 ? ids.iterator().next() : null;
    }

    private BigDecimal calcCouponDiscount(BigDecimal s, String type, BigDecimal val) {
        if (val == null) return BigDecimal.ZERO;
        return switch (type) {
            case "FULL_REDUCTION", "NO_THRESHOLD" ->
                val.compareTo(s) > 0 ? s : val;
            case "DISCOUNT" ->
                s.multiply(BigDecimal.ONE.subtract(val)).setScale(2, RoundingMode.HALF_UP);
            default -> BigDecimal.ZERO;
        };
    }

    @lombok.AllArgsConstructor
    private static class Recalculated {
        final PromotionContext.CouponCandidate original;
        final BigDecimal effectiveSubtotal;
        final BigDecimal effectiveDiscount;
    }
}
