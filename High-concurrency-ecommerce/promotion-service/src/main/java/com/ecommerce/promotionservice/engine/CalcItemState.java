package com.ecommerce.promotionservice.engine;

import com.ecommerce.dto.promotion.PromoCalcItem;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 引擎内部行项状态（mutable，伴随整个管线生命周期）。
 *
 * 与 PromoCalcItem 的关系：
 * - PromoCalcItem = 外部输入的不可变快照
 * - CalcItemState  = 内部各 Layer 逐层改写的工作副本
 */
@Getter
@Setter
public class CalcItemState {

    // ======== 从 PromoCalcItem 复制过来的不可变字段 ========
    private final Long skuId;
    private final Long productId;
    private final Long merchantId;
    private final Long categoryId;
    private final BigDecimal originPrice;   // 下单时的原始单价（不可变，退款还原用）
    private final int quantity;

    // ======== 管线可变状态 ========

    /** 当前单价：
     *  Layer 1 前 = originPrice；
     *  Layer 1 命中秒杀 → seckillPrice；
     *  Layer 2/3 可能继续折减（满减/券作用于 whole group，单价本身不变，这里是组内试算价） */
    private BigDecimal currentPrice;

    /** 当前行小计 = currentPrice × quantity（Layer 2/3/5 都用它分摊比例） */
    private BigDecimal subtotal;

    // ---- Layer 1 写入 ----
    /** 是否命中秒杀（命中则退出 L2~L5） */
    private boolean seckillHit;
    /** 命中的秒杀活动 ID */
    private Long seckillActivityId;
    /** 秒杀价（Layer 1 替换 currentPrice 为此值） */
    private BigDecimal seckillPrice;

    // ---- Layer 2 写入 ----
    /** 命中的促销活动 ID */
    private Long matchedActivityId;
    /** 命中的阶梯规则 ID */
    private Long matchedRuleId;

    // ---- Layer 2/3/4 累计 ----
    /** 该行已累计的优惠来源明细（Layer 4 裁决后的最终候选） */
    private final List<HitEntry> hits = new ArrayList<>();

    // ---- Layer 5 写入 ----
    /** 最终分摊到这一行的优惠金额（元，到分） */
    private BigDecimal allocatedDiscount = BigDecimal.ZERO;

    // ======== 构造 ========

    public CalcItemState(PromoCalcItem item) {
        this.skuId = item.getSkuId();
        this.productId = item.getProductId();
        this.merchantId = item.getMerchantId();
        this.categoryId = item.getCategoryId();
        this.originPrice = item.getPrice();
        this.quantity = item.getQuantity();
        this.currentPrice = item.getPrice();
        this.subtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    // ======== 内嵌 ========

    /**
     * 命中记录（每层命中时追加一条，最终输出到 trace）
     */
    @Getter
    @lombok.AllArgsConstructor
    public static class HitEntry {
        private final String layer;       // SEC / ACT / COU / OVL / ALOC
        private final String refType;     // SECKILL / PROMOTION / COUPON
        private final Long refId;         // 实体 ID
        private final BigDecimal amount;  // 贡献金额
    }

    // ======== 便捷方法 ========

    /** Layer 1 调用：替换为秒杀价 + 打互斥标记 */
    public void applySeckill(Long activityId, BigDecimal seckillPrice) {
        this.seckillHit = true;
        this.seckillActivityId = activityId;
        this.seckillPrice = seckillPrice;
        this.currentPrice = seckillPrice;
        this.subtotal = seckillPrice.multiply(BigDecimal.valueOf(this.quantity));
    }

    /** 标记不再参与后续优惠（秒杀、N元M件命中后调用） */
    public void excludeFromFurtherPromos() {
        this.seckillHit = true;   // 后续层算 subtotal 时不参与
    }
}
