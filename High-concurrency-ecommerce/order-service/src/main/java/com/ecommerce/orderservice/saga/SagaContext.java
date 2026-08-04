package com.ecommerce.orderservice.saga;

import com.ecommerce.orderservice.entity.OrderItem;
import com.ecommerce.dto.promotion.PromotionLockResponse;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class SagaContext {
    private String orderNo;          // 订单号
    private Long userId;             // 用户ID
    private BigDecimal payAmount;    // 实付金额
    private List<OrderItem> items;   // 订单明细
    private List<String> couponCodes;  // 使用的优惠券ID
    private String payNo;            // execute 返回后由编排器塞入，补偿时用

    /** 商品ID → 分类ID（Saga 启动前由编排器批量查 product 表填入） */
    private Map<Long, Long> productCategoryMap;

    /** 锁券返回的优惠总额（LockPromotionStep 执行后填入，后续计算 payAmount 用） */
    private BigDecimal discountAmount;

    /** 锁券完整响应（含按商家分摊 splits / 行级分摊 items），供下单后回写 order_split/order_item */
    private PromotionLockResponse lockResponse;
}
