package com.ecommerce.promotionservice.engine;

import com.ecommerce.dto.promotion.PromoCalcItem;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 五层引擎管线上下文——所有 Step 共享的"黑板"。
 *
 * 生命周期：DiscountEngine.calculate() 创建 → 五个 Step 依次读写 → 返回给上层。
 */
@Data
public class PromotionContext {

    // ======== 输入（构造时填入，只读） ========
    private final Long userId;
    private final List<String> couponCodes;      // 可为空（preview 不选券）
    private final CalcMode mode;

    // ======== 核心载体（每个 Step 改它） ========
    private final List<CalcItemState> items;

    // ======== Layer 2/3 中间结果（OverlapResolveStep 需要跨类型裁决） ========
    /** 活动匹配结果：key=activityId, value={命中该活动的 items 的 subTotal 合计, discountAmount} */
    private final Map<Long, ActivityCandidate> activityCandidates = new LinkedHashMap<>();

    /** 券候选：Layer 3 试算后放入，Layer 4 裁决时按金额排序择优 */
    private final List<CouponCandidate> couponCandidates = new ArrayList<>();

    /** Layer 4 裁决后的获胜活动（DiscountAllocateStep 读取） */
    private final List<ActivityCandidate> winningActivities = new ArrayList<>();

    /** Layer 4 裁决后的获胜券 */
    private final List<CouponCandidate> winningCoupons = new ArrayList<>();


    // ======== LOCK 模式专用：Lua 脚本入参的数据源 ========
    /** activityId → 需扣减的预算额（分），Layer 4 裁决后填入，PromotionLockService 读取转 ARGV */
    private final Map<Long, Long> budgetDeductions = new HashMap<>();
    /** activityId → 需递增的每日计数 */
    private final Map<Long, Long> dailyIncrements = new HashMap<>();

    // ======== 输出 ========
    private BigDecimal totalDiscount = BigDecimal.ZERO;
    private final List<CalcItemState.HitEntry> trace = new ArrayList<>();

    // ======== 构造 ========

    public PromotionContext(Long userId, List<String> couponCodes,
                            List<PromoCalcItem> inputItems, CalcMode mode) {
        this.userId = userId;
        this.couponCodes = couponCodes != null ? couponCodes : List.of();
        this.mode = mode;
        this.items = inputItems.stream().map(CalcItemState::new).collect(Collectors.toList());
    }

    // ======== 便捷查询 ========

    /** 获取排除秒杀/N元M件后的"后续可参与优惠"的行项 */
    public List<CalcItemState> getActiveItems() {
        return items.stream()
            .filter(i -> !i.isSeckillHit())
            .collect(Collectors.toList());
    }

    /** 按 merchantId 分组（Layer 2 入口） */
    public Map<Long, List<CalcItemState>> groupByMerchant() {
        return getActiveItems().stream()
            .collect(Collectors.groupingBy(CalcItemState::getMerchantId));
    }

    // ======== 内嵌 ========

    @Data
    @AllArgsConstructor
    public static class ActivityCandidate {
        private final Long activityId;
        private BigDecimal groupSubtotal;    // 命中组的 subtotal 合计
        private BigDecimal discountAmount;   // 匹配阶梯后的优惠额
        private final List<CalcItemState> matchedItems;
    }

    @Data
    @AllArgsConstructor
    public static class CouponCandidate {
        private final String couponCode;
        private final String couponType;        // FULL_REDUCTION / DISCOUNT / NO_THRESHOLD
        private final BigDecimal discountValue; // 券面优惠值
        private final BigDecimal thresholdAmount;
        private final boolean stackable;
        private BigDecimal applicableSubtotal;  // 可作用金额合计
        private BigDecimal calculatedDiscount;  // 试算出的优惠金额
        private final List<CalcItemState> applicableItems;  // 该券可作用的 items
    }
}
