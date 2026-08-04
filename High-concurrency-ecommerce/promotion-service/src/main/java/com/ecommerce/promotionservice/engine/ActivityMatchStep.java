package com.ecommerce.promotionservice.engine;

import com.ecommerce.promotionservice.entity.PromotionActivity;
import com.ecommerce.promotionservice.entity.PromotionRule;
import com.ecommerce.promotionservice.entity.PromotionScope;
import com.ecommerce.promotionservice.mapper.PromotionActivityMapper;
import com.ecommerce.promotionservice.mapper.PromotionRuleMapper;
import com.ecommerce.promotionservice.mapper.PromotionScopeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Layer 2 — 促销活动匹配（满减 / 满折 / N元M件）。
 *
 * 1. 取 Layer 1 输出的非秒杀商品，按 merchantId 分组
 * 2. 对每组，查询 status=1 的 ongoing promotion_activity
 * 3. 通过 promotion_scope 过滤适用范围（无 scope=全场通用）
 * 4. 匹配 promotion_rule 的阶梯区间 [minAmount, maxAmount)
 * 5. 命中的候选写入 ctx.activityCandidates，由 Layer 4 裁决
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class ActivityMatchStep implements CalcStep {

    private final PromotionActivityMapper activityMapper;
    private final PromotionRuleMapper ruleMapper;
    private final PromotionScopeMapper scopeMapper;

    @Override
    public void apply(PromotionContext ctx) {
        // 1. 获取非秒杀商品，按商家分组
        Map<Long, List<CalcItemState>> merchantGroups = ctx.groupByMerchant();
        if (merchantGroups.isEmpty()) {
            log.debug("无有效商品分组，跳过活动匹配");
            return;
        }

        // 2. 一次性查出所有进行中的活动
        List<PromotionActivity> ongoingActivities = activityMapper.selectOngoing();
        if (ongoingActivities.isEmpty()) {
            log.debug("无进行中活动");
            return;
        }

        // 3. 遍历每个商家分组
        for (Map.Entry<Long, List<CalcItemState>> entry : merchantGroups.entrySet()) {
            matchGroup(ctx, entry.getKey(), entry.getValue(), ongoingActivities);
        }
    }

    // ==================== 分组级 ====================

    private void matchGroup(PromotionContext ctx, Long merchantId,
                            List<CalcItemState> groupItems,
                            List<PromotionActivity> ongoingActivities) {

        List<Long> activityIds = ongoingActivities.stream()
            .map(PromotionActivity::getId)
            .collect(Collectors.toList());

        // 批量查 scope
        Map<Long, List<PromotionScope>> scopesByActivity =
            scopeMapper.selectByTargets("PROMOTION", activityIds)
                .stream()
                .collect(Collectors.groupingBy(PromotionScope::getTargetId));

        for (PromotionActivity activity : ongoingActivities) {
            tryMatchActivity(ctx, groupItems, activity,
                scopesByActivity.getOrDefault(activity.getId(), List.of()));
        }
    }

    // ==================== 活动级 ====================

    private void tryMatchActivity(PromotionContext ctx,
                                  List<CalcItemState> groupItems,
                                  PromotionActivity activity,
                                  List<PromotionScope> scopes) {

        // 按 scope 过滤
        List<CalcItemState> matchedItems = scopes.isEmpty()
            ? groupItems
            : filterByScope(groupItems, scopes);

        if (matchedItems.isEmpty()) return;

        // 计算组小计 & 总件数
        BigDecimal groupSubtotal = matchedItems.stream()
            .map(CalcItemState::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalQuantity = matchedItems.stream()
            .mapToInt(CalcItemState::getQuantity)
            .sum();

        // 匹配阶梯
        List<PromotionRule> rules = ruleMapper.selectByActivityId(activity.getId());
        if (rules.isEmpty()) return;

        BigDecimal discount = switch (activity.getActivityType()) {
            case "FULL_REDUCTION", "FULL_DISCOUNT" ->
                matchAmountBased(rules, groupSubtotal, activity.getActivityType());
            case "N_M" ->
                matchN_M(rules, matchedItems, totalQuantity);
            default -> {
                log.warn("未知活动类型: {}", activity.getActivityType());
                yield null;
            }
        };

        if (discount == null) return;

        // 写入上下文
        ctx.getActivityCandidates().put(activity.getId(),
            new PromotionContext.ActivityCandidate(
                activity.getId(), groupSubtotal, discount, matchedItems));

        log.debug("活动 {} 命中: type={}, subtotal={}, discount={}",
            activity.getId(), activity.getActivityType(), groupSubtotal, discount);
    }

    // ==================== Scope 过滤 ====================

    private List<CalcItemState> filterByScope(List<CalcItemState> items,
                                              List<PromotionScope> scopes) {
        Set<Long> productIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> merchantIds = new HashSet<>();

        for (PromotionScope s : scopes) {
            switch (s.getScopeType()) {
                case "PRODUCT"  -> productIds.add(s.getScopeId());
                case "CATEGORY" -> categoryIds.add(s.getScopeId());
                case "MERCHANT" -> merchantIds.add(s.getScopeId());
            }
        }

        return items.stream()
            .filter(item ->
                productIds.contains(item.getProductId())
                    || categoryIds.contains(item.getCategoryId())
                    || merchantIds.contains(item.getMerchantId()))
            .collect(Collectors.toList());
    }

    // ==================== 满减/满折匹配 ====================

    private BigDecimal matchAmountBased(List<PromotionRule> rules,
                                        BigDecimal subtotal, String activityType) {
        BigDecimal bestDiscount = null;

        for (PromotionRule rule : rules) {
            BigDecimal min = rule.getMinAmount();
            BigDecimal max = rule.getMaxAmount();
            if (min != null && subtotal.compareTo(min) < 0) continue;
            if (max != null && subtotal.compareTo(max) >= 0) continue;  // 不包含上界

            BigDecimal d = calculateAmountDiscount(subtotal, rule);
            if (d != null && (bestDiscount == null || d.compareTo(bestDiscount) > 0)) {
                bestDiscount = d;
            }
        }
        return bestDiscount;
    }

    private BigDecimal calculateAmountDiscount(BigDecimal subtotal, PromotionRule rule) {
        return switch (rule.getDiscountType()) {
            case "FIXED_AMOUNT" -> {
                BigDecimal d = rule.getDiscountValue();
                yield d.compareTo(subtotal) > 0 ? subtotal : d;
            }
            case "PERCENTAGE" -> {
                BigDecimal ratio = BigDecimal.ONE.subtract(rule.getDiscountValue());
                yield subtotal.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            }
            default -> {
                log.warn("未知 discountType: {}", rule.getDiscountType());
                yield null;
            }
        };
    }

    // ==================== N元M件匹配 ====================

    private BigDecimal matchN_M(List<PromotionRule> rules,
                                List<CalcItemState> matchedItems,
                                int totalQuantity) {
        BigDecimal bestDiscount = null;

        for (PromotionRule rule : rules) {
            int M = rule.getMinAmount() != null ? rule.getMinAmount().intValue() : 0;
            if (M <= 0 || totalQuantity < M) continue;

            BigDecimal N = rule.getDiscountValue();  // 打包价

            // 按单价升序
            List<CalcItemState> sorted = matchedItems.stream()
                .sorted(Comparator.comparing(CalcItemState::getCurrentPrice))
                .collect(Collectors.toList());

            int groupCount = totalQuantity / M;
            int effectiveQty = M * groupCount;

            BigDecimal originalSubtotal = BigDecimal.ZERO;
            int taken = 0;
            for (CalcItemState item : sorted) {
                if (taken >= effectiveQty) break;
                int toTake = Math.min(item.getQuantity(), effectiveQty - taken);
                originalSubtotal = originalSubtotal.add(
                    item.getCurrentPrice().multiply(BigDecimal.valueOf(toTake)));
                taken += toTake;
            }

            BigDecimal packTotal = N.multiply(BigDecimal.valueOf(groupCount));
            BigDecimal discount = originalSubtotal.subtract(packTotal);
            if (discount.compareTo(BigDecimal.ZERO) < 0) {
                discount = BigDecimal.ZERO;
            }

            if (bestDiscount == null || discount.compareTo(bestDiscount) > 0) {
                bestDiscount = discount;
            }
        }
        return bestDiscount;
    }
}
