package com.ecommerce.searchservice.service;

import com.ecommerce.client.ProductClient;
import com.ecommerce.dto.product.ProductSummaryDTO;
import com.ecommerce.result.Result;
import com.ecommerce.searchservice.config.RabbitConfig;
import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * ES 索引同步服务。
 *
 * 监听 product.exchange 上的商品事件，按 routingKey 分发：
 *   - product.created           → 构建 ProductDocument，写入索引
 *   - product.updated           → 拉最新数据，写入索引（覆盖）
 *   - product.status.changed    → 下架（newStatus=0）从索引删除；上架（newStatus=1）重新同步
 *
 * 队列：search.product.sync.queue（见 RabbitConfig）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexSyncService {

    private final ProductSearchRepository searchRepository;
    private final ProductClient productClient;

    /** 统一入口，按 routingKey 分发 */
    @RabbitListener(queues = RabbitConfig.SEARCH_PRODUCT_SYNC_QUEUE)
    public void onEvent(Map<String, Object> payload,
                        @Header(name = "amqp_receivedRoutingKey", required = false) String routingKey) {
        if (routingKey == null) {
            log.warn("收到无 routingKey 的消息，忽略 payload={}", payload);
            return;
        }
        log.debug("[search-sync] 收到事件 routingKey={} payload={}", routingKey, payload);
        try {
            switch (routingKey) {
                case "product.created"        -> handleCreated(payload);
                case "product.updated"        -> handleUpdated(payload);
                case "product.status.changed" -> handleStatusChanged(payload);
                default -> log.warn("[search-sync] 未知事件类型: {}", routingKey);
            }
        } catch (Exception e) {
            // 抛异常 → MQ 触发重试 / 进入死信队列
            log.error("[search-sync] 处理失败 routingKey={} payload={}", routingKey, payload, e);
            throw e;
        }
    }

    // ==================== 处理逻辑 ====================

    /** product.created → 直接用事件数据构建文档（event 自带基本字段） */
    private void handleCreated(Map<String, Object> payload) {
        Long productId = getLong(payload, "productId");
        if (productId == null) {
            log.warn("[search-sync] product.created 缺少 productId");
            return;
        }
        ProductDocument doc = new ProductDocument();
        doc.setId(productId);
        doc.setName((String) payload.get("name"));
        doc.setCategoryId(getLong(payload, "categoryId"));
        doc.setPrice(toBigDecimal(payload.get("minPrice")));
        // status 默认为 1（上架）—— 新建的商品默认上架
        doc.setStatus(1);
        // 销量/评分/品牌/分类名/图片 新建事件不带，默认 0 / null，后续更新事件会补
        doc.setSalesCount(0);
        doc.setMainImage(null);

        searchRepository.save(doc);
        log.info("[search-sync] 已索引新商品 productId={}", productId);
    }

    /** product.updated → 从 product-service 拉最新数据全量覆盖 */
    private void handleUpdated(Map<String, Object> payload) {
        Long productId = getLong(payload, "productId");
        if (productId == null) {
            log.warn("[search-sync] product.updated 缺少 productId");
            return;
        }
        ProductDocument doc = fetchFullDocument(productId);
        if (doc == null) return;

        searchRepository.save(doc);
        log.info("[search-sync] 已更新商品索引 productId={}", productId);
    }

    /**
     * product.status.changed
     *   newStatus = 0（下架）→ 从 ES 索引删除
     *   newStatus = 1（上架）→ 拉最新数据重新同步
     */
    private void handleStatusChanged(Map<String, Object> payload) {
        Long productId = getLong(payload, "productId");
        Integer newStatus = getInt(payload, "newStatus");
        if (productId == null || newStatus == null) {
            log.warn("[search-sync] product.status.changed 字段缺失 payload={}", payload);
            return;
        }

        if (newStatus == 0) {
            searchRepository.deleteById(productId);
            log.info("[search-sync] 商品下架，已移出索引 productId={}", productId);
        } else if (newStatus == 1) {
            ProductDocument doc = fetchFullDocument(productId);
            if (doc != null) {
                searchRepository.save(doc);
                log.info("[search-sync] 商品上架，已重新索引 productId={}", productId);
            }
        } else {
            log.warn("[search-sync] 未知 status 值 productId={} newStatus={}", productId, newStatus);
        }
    }

    // ==================== 拉取完整数据 ====================

    /** 通过 ProductClient 调 product-service 拉商品摘要数据。 */
    private ProductDocument fetchFullDocument(Long productId) {
        try {
            Result<ProductSummaryDTO> result = productClient.getById(productId);
            if (result == null || result.getCode() != 200 || result.getData() == null) {
                log.warn("[search-sync] 拉取商品详情失败 productId={} result={}", productId, result);
                return null;
            }
            return toDocument(result.getData());
        } catch (Exception e) {
            log.error("[search-sync] Feign 调用 product-service 异常 productId={}", productId, e);
            return null;
        }
    }

    /** ProductSummaryDTO → ProductDocument */
    private ProductDocument toDocument(ProductSummaryDTO data) {
        ProductDocument doc = new ProductDocument();
        doc.setId(data.getId());
        doc.setName(data.getName());
        doc.setCategoryId(data.getCategoryId());
        doc.setMainImage(data.getMainImage());
        doc.setPrice(data.getMinPrice());
        doc.setSalesCount(data.getSales() != null ? data.getSales().intValue() : 0);
        doc.setStatus(data.getStatus() != null ? data.getStatus() : 0);
        return doc;
    }

    // ==================== 工具方法 ====================

    private static Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    private static Integer getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(o.toString());
    }
}
