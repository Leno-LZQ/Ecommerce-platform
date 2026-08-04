package com.ecommerce.promotionservice.event;

import com.ecommerce.promotionservice.config.PromotionProperties;
import com.ecommerce.promotionservice.service.CouponService;
import com.ecommerce.promotionservice.service.PromotionLockService;
import com.ecommerce.promotionservice.mapper.UserCouponTagMapper;
import com.ecommerce.promotionservice.entity.UserCouponTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 优惠引擎事件消费者。
 *
 * 队列：promotion.queue
 * 绑定：user.exchange/user.registered + order.exchange/order.paid/cancelled/refunded
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionEventConsumer {

    private final CouponService couponService;
    private final PromotionLockService promotionLockService;
    private final UserCouponTagMapper userCouponTagMapper;
    private final PromotionProperties props;

    @RabbitListener(queues = "promotion.queue")
    public void onEvent(Map<String, Object> payload,
                        @Header(name = "amqp_receivedRoutingKey", required = false) String routingKey) {
        if (routingKey == null) {
            log.warn("收到无 routingKey 的消息，忽略");
            return;
        }

        log.debug("收到事件 routingKey={} payload={}", routingKey, payload);

        switch (routingKey) {
            case "user.registered" -> handleUserRegistered(payload);
            case "order.paid" -> handleOrderPaid(payload);
            case "order.cancelled" -> handleOrderCancelled(payload);
            case "order.refunded" -> handleOrderRefunded(payload);
            default -> log.warn("未知事件类型: {}", routingKey);
        }
    }

    // ==================== 事件处理 ====================

    private void handleUserRegistered(Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        if (userId == null) return;

        // 幂等：查重
        var existing = userCouponTagMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserCouponTag>()
                .eq(UserCouponTag::getUserId, userId)
                .eq(UserCouponTag::getTagType, "NEW_USER"));
        if (!existing.isEmpty()) {
            log.debug("用户 {} 已打标 NEW_USER，跳过", userId);
            return;
        }

        // 打标
        UserCouponTag tag = new UserCouponTag();
        tag.setUserId(userId);
        tag.setTagType("NEW_USER");
        tag.setTagValue("");
        userCouponTagMapper.insert(tag);

        // 自动发新人券
        for (Long templateId : props.getNewUserTemplateIds()) {
            try {
                couponService.issueCouponByTemplate(userId, templateId);
            } catch (Exception e) {
                log.error("新人券发放失败 userId={} templateId={}", userId, templateId, e);
            }
        }

        log.info("新人注册处理完成 userId={}", userId);
    }

    private void handleOrderPaid(Map<String, Object> payload) {
        String orderNo = getString(payload, "orderNo");
        if (orderNo == null) return;
        try {
            promotionLockService.confirm(orderNo);
        } catch (Exception e) {
            log.error("order.paid 处理失败 orderNo={}", orderNo, e);
            throw e; // 抛异常触发重试
        }
    }

    private void handleOrderCancelled(Map<String, Object> payload) {
        String orderNo = getString(payload, "orderNo");
        if (orderNo == null) return;
        try {
            promotionLockService.release(orderNo);
        } catch (Exception e) {
            log.error("order.cancelled 处理失败 orderNo={}", orderNo, e);
            throw e;
        }
    }

    private void handleOrderRefunded(Map<String, Object> payload) {
        String orderNo = getString(payload, "orderNo");
        if (orderNo == null) return;

        Object ratioObj = payload.get("refundRatio");
        java.math.BigDecimal ratio = java.math.BigDecimal.ONE;
        if (ratioObj instanceof Number n) {
            ratio = java.math.BigDecimal.valueOf(n.doubleValue());
        }

        try {
            var req = new com.ecommerce.dto.promotion.RefundRequest();
            req.setOrderNo(orderNo);
            req.setRefundRatio(ratio);
            promotionLockService.refund(req);
        } catch (Exception e) {
            log.error("order.refunded 处理失败 orderNo={}", orderNo, e);
            throw e;
        }
    }

    // ==================== 工具 ====================

    private Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
