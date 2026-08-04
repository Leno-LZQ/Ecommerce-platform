package com.ecommerce.promotionservice.engine;

/**
 * 管线步骤接口——每个 Step 实现此接口，由 DiscountEngine 按 @Order 依次调用。
 */
public interface CalcStep {
    /** 对上下文执行本层计算（只读 Mapper/Redis，写结果到 ctx，不做任何 DB/Redis 写） */
    void apply(PromotionContext ctx);
}
