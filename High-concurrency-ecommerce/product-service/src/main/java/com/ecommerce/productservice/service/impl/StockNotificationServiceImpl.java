package com.ecommerce.productservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.client.InventoryClient;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.productservice.entity.ProductSKU;
import com.ecommerce.productservice.entity.StockNotification;
import com.ecommerce.productservice.mapper.ProductSkuMapper;
import com.ecommerce.productservice.mapper.StockNotificationMapper;
import com.ecommerce.productservice.service.StockNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockNotificationServiceImpl implements StockNotificationService {

    private final StockNotificationMapper notificationMapper;
    private final ProductSkuMapper skuMapper;
    private final InventoryClient inventoryClient;

    @Override
    @Transactional
    public void subscribe(Long skuId, Long userId) {
        // ① 校验 SKU 存在
        ProductSKU sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.SKU_NOT_FOUND);
        }

        // ② Feign 查实时库存
        Integer stock = null;
        try {
            var result = inventoryClient.getStock(skuId);
            stock = result != null ? result.getData() : null;
        } catch (Exception e) {
            log.error("查库存失败，降级查 MySQL，skuId={}", skuId, e);
            stock = sku.getStock();  // 降级：用 MySQL 缓存值
        }

        if (stock != null && stock > 0) {
            throw new BusinessException(ErrorCode.NOTIFY_SKU_HAS_STOCK);
        }

        // ③ 防重复订阅
        Long count = notificationMapper.selectCount(
            new LambdaQueryWrapper<StockNotification>()
                .eq(StockNotification::getSkuId, skuId)
                .eq(StockNotification::getUserId, userId)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.NOTIFY_ALREADY_SUBSCRIBED);
        }

        // ④ 插入
        StockNotification notification = new StockNotification();
        notification.setSkuId(skuId);
        notification.setUserId(userId);
        notification.setNotified(0);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);

        log.info("到货通知订阅成功: skuId={}, userId={}", skuId, userId);
    }

    @Override
    @Transactional
    public void unsubscribe(Long skuId, Long userId) {
        LambdaQueryWrapper<StockNotification> wrapper =
            new LambdaQueryWrapper<StockNotification>()
                .eq(StockNotification::getSkuId, skuId)
                .eq(StockNotification::getUserId, userId);

        StockNotification record = notificationMapper.selectOne(wrapper);
        if (record == null) {
            return;  // 幂等：没订阅过就当已取消
        }
        notificationMapper.deleteById(record.getId());
        log.info("到货通知取消: skuId={}, userId={}", skuId, userId);
    }
}
