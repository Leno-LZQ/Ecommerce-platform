package com.ecommerce.productservice.service;

public interface StockNotificationService {
    /** 订阅到货通知 */
    void subscribe(Long skuId, Long userId);

    /** 取消订阅 */
    void unsubscribe(Long skuId, Long userId);
}
