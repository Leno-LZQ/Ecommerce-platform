package com.ecommerce.promotionservice.engine;

/**
 * 计算模式：preview 和 lock 共用 DiscountEngine 管线，
 * 差异仅在于 LOCK 模式会执行 Redis/DB 写操作。
 */
public enum CalcMode {
    /** 购物车预览：只读、不锁定、不扣名额 */
    PREVIEW,
    /** 下单锁定：计算后由 PromotionLockService 执行 Redis Lua + DB 写入 */
    LOCK
}
