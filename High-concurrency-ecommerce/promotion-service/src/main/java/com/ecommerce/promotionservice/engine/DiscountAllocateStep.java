package com.ecommerce.promotionservice.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Layer 5 — 优惠金额按行分摊。
 *
 * 把 Layer 4 裁决出的活动+券优惠总额，按每个商品的小计比例
 * 精确分摊到每一行（到分，差额归末行）。
 * 同时按 merchantId 汇总，供 order-service 写 order_split。
 *
 * 核心算法：末行兜底法
 *   前 N-1 行：discount × (item.subtotal / groupSubtotal)，HALF_UP 到分
 *   第 N 行：discount - 前 N-1 行之合
 *   保证 Σ 行 = 总额，不差一分钱
 */
@Slf4j
@Component
@Order(5)
public class DiscountAllocateStep implements CalcStep {

    @Override
    public void apply(PromotionContext ctx) {

        // ---- 初始化：所有商品优惠清零 ----
        for (CalcItemState item : ctx.getItems()) {
            item.setAllocatedDiscount(BigDecimal.ZERO);
        }

        // ---- 阶段1：分摊活动优惠 ----
        // 活动 candidate 里的 matchedItems 就是该活动作用的商品组
        for (PromotionContext.ActivityCandidate ac : ctx.getWinningActivities()) {
            allocateGroup(ac.getMatchedItems(), ac.getGroupSubtotal(),
                ac.getDiscountAmount());
        }

        // ---- 阶段2：分摊券优惠 ----
        // 券 candidate 里的 applicableItems 就是该券作用的商品组
        for (PromotionContext.CouponCandidate cc : ctx.getWinningCoupons()) {
            allocateGroup(cc.getApplicableItems(), cc.getApplicableSubtotal(),
                cc.getCalculatedDiscount());
        }

        // ---- 验证：Σ 行 = 总额 ----
        BigDecimal actual = ctx.getItems().stream()
            .map(CalcItemState::getAllocatedDiscount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (actual.compareTo(ctx.getTotalDiscount()) != 0) {
            log.error("分摊精度异常！预期={}, 实际={}, 差额={}",
                ctx.getTotalDiscount(), actual,
                ctx.getTotalDiscount().subtract(actual));
        } else {
            log.info("Layer5 分摊完成: 总额={}, {} 行商品", actual, ctx.getItems().size());
        }

        // ---- 阶段3：生成 trace ----
        // 添加商品级 trace
        for (CalcItemState item : ctx.getItems()) {
            if (item.getAllocatedDiscount().compareTo(BigDecimal.ZERO) > 0) {
                item.getHits().add(new CalcItemState.HitEntry(
                    "L5", "ALLOC", item.getSkuId(), item.getAllocatedDiscount()));
            }
        }
    }

    // ========== 核心分摊算法：比例分配 + 末行兜底 ==========

    /**
     * 将 discount 按 subtotal 比例分摊到 items 列表的每一行。
     *
     * 末行兜底保证 Σ行 = discount（精确到分）。
     * 注意：item.getAllocatedDiscount 是累加的（可能已从其他 winner 分摊过）。
     */
    private void allocateGroup(List<CalcItemState> items,
                               BigDecimal groupSubtotal,
                               BigDecimal discount) {
        if (items.isEmpty() || discount.compareTo(BigDecimal.ZERO) == 0) return;

        int n = items.size();
        BigDecimal allocated = BigDecimal.ZERO;

        // 前 N-1 行：按比例算，HALF_UP 到分
        for (int i = 0; i < n - 1; i++) {
            CalcItemState item = items.get(i);

            // share = discount × (item.subtotal / groupSubtotal)
            BigDecimal ratio = item.getSubtotal()
                .divide(groupSubtotal, 6, RoundingMode.HALF_UP);
            BigDecimal share = discount.multiply(ratio)
                .setScale(2, RoundingMode.HALF_UP);

            // 累加到该行（可能叠加多个 winner 的分摊）
            item.setAllocatedDiscount(item.getAllocatedDiscount().add(share));
            allocated = allocated.add(share);
        }

        // 最后一行：兜底 = 总额 - 前 N-1 行之和
        CalcItemState last = items.get(n - 1);
        BigDecimal remainder = discount.subtract(allocated);
        last.setAllocatedDiscount(last.getAllocatedDiscount().add(remainder));
    }
}
