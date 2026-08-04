package com.ecommerce.promotionservice.event;

import com.ecommerce.promotionservice.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 优惠引擎事件发布。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 券已核销。
     */
    public void publishCouponUsed(String orderNo, String couponCode,
                                   BigDecimal discountAmount, String merchantSplitsJson) {
        Map<String, Object> event = Map.of(
            "orderNo", orderNo,
            "couponCode", couponCode,
            "discountAmount", discountAmount,
            "splits", merchantSplitsJson
        );
        rabbitTemplate.convertAndSend(
            RabbitConfig.PROMOTION_EXCHANGE, "promotion.coupon.used", event);
        log.debug("发布事件 promotion.coupon.used orderNo={}", orderNo);
    }

    /**
     * 券已退还（退款）。
     */
    public void publishCouponRefunded(String orderNo, String couponCode, BigDecimal refundAmount) {
        Map<String, Object> event = Map.of(
            "orderNo", orderNo,
            "couponCode", couponCode,
            "refundAmount", refundAmount
        );
        rabbitTemplate.convertAndSend(
            RabbitConfig.PROMOTION_EXCHANGE, "promotion.coupon.refunded", event);
        log.debug("发布事件 promotion.coupon.refunded orderNo={}", orderNo);
    }

    /**
     * 活动预算告警。
     */
    public void publishBudgetLow(Long activityId, double remainingRatio) {
        Map<String, Object> event = Map.of(
            "activityId", activityId,
            "remainingRatio", remainingRatio
        );
        rabbitTemplate.convertAndSend(
            RabbitConfig.PROMOTION_EXCHANGE, "promotion.activity.budget.low", event);
        log.warn("发布事件 promotion.activity.budget.low activityId={} remainingRatio={}",
            activityId, remainingRatio);
    }
}
