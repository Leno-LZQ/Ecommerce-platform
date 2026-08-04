package com.ecommerce.dto.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 下单锁定优惠响应（order-service 据此写 orders/order_item/order_split.discount_amount）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionLockResponse {

    /** 订单号（回传，方便 order 侧对账） */
    private String orderNo;

    /** 总优惠金额（元） */
    private BigDecimal totalDiscount;

    /** 行级分摊明细 */
    private List<ItemDiscount> items;

    /** 按商家汇总（写入 order_split.discount_amount） */
    private List<MerchantSplit> splits;

    /** 命中追踪明细（排查"为什么是这个价"用） */
    private List<PromotionTrace> trace;

    public PromotionLockResponse(String orderNo, BigDecimal totalDiscount, List<ItemDiscount> itemDiscounts, List<MerchantSplit> splits) {
        this.orderNo = orderNo;
        this.totalDiscount = totalDiscount;
        this.items = itemDiscounts;
        this.splits = splits;
    }

    // ========== 内嵌结构 ==========

    /**
     * 行级优惠分摊
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDiscount {
        /** SKU ID */
        private Long skuId;
        /** 该行分摊的优惠金额（元，到分，末行兜底确保分毫不差） */
        private BigDecimal discountAmount;
    }

    /**
     * 按商家汇总（用于 order_split）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantSplit {
        /** 商家 ID */
        private Long merchantId;
        /** 该商家子订单的优惠总额（元） */
        private BigDecimal discountAmount;
    }

    /**
     * 命中追踪
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionTrace {
        /** 计算层：SEC / ACT / COU / OVL / ALOC */
        private String layer;
        /** 实体类型：SECKILL / PROMOTION / COUPON */
        private String refType;
        /** 实体 ID */
        private Long refId;
        /** 该实体贡献的优惠金额（元） */
        private BigDecimal amount;
    }
}
