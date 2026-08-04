package com.ecommerce.merchantservice.event;

import com.ecommerce.merchantservice.entity.Merchant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MerchantEventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String MERCHANT_EXCHANGE = "merchant.exchange";

    // ==================== routing key 常量 ====================
    private static final String ROUTING_APPROVED = "merchant.approved";
    private static final String ROUTING_REJECTED = "merchant.rejected";
    private static final String ROUTING_FROZEN   = "merchant.frozen";
    private static final String ROUTING_UNFROZEN = "merchant.unfrozen";
    private static final String ROUTING_CLOSED   = "merchant.closed";

    // ==================== publishApproved ====================
    /** 审核通过：PENDING → ACTIVE */
    public void publishApproved(Merchant merchant, String remark) {
        MerchantEvent event = MerchantEvent.builder()
            .merchantId(merchant.getId())
            .userId(merchant.getUserId())
            .shopName(merchant.getShopName())
            .status(merchant.getStatus())       // = 1 (ACTIVE)
            .remark(remark)
            .build();
        send(ROUTING_APPROVED, event);
        log.info("商家审核通过事件已发布: merchantId={}, shopName={}", merchant.getId(), merchant.getShopName());
    }

    // ==================== publishRejected ====================
    /** 审核拒绝：PENDING → REJECTED */
    public void publishRejected(Merchant merchant, String remark) {
        MerchantEvent event = MerchantEvent.builder()
            .merchantId(merchant.getId())
            .userId(merchant.getUserId())
            .shopName(merchant.getShopName())
            .status(merchant.getStatus())       // = 2 (REJECTED)
            .remark(remark)
            .build();
        send(ROUTING_REJECTED, event);
        log.info("商家审核拒绝事件已发布: merchantId={}, remark={}", merchant.getId(), remark);
    }

    // ==================== publishFrozen ====================
    /** 冻结：ACTIVE → FROZEN */
    public void publishFrozen(Merchant merchant, String reason) {
        MerchantEvent event = MerchantEvent.builder()
            .merchantId(merchant.getId())
            .userId(merchant.getUserId())
            .shopName(merchant.getShopName())
            .status(merchant.getStatus())       // = 3 (FROZEN)
            .remark(reason)
            .build();
        send(ROUTING_FROZEN, event);
        log.warn("商家冻结事件已发布: merchantId={}, reason={}", merchant.getId(), reason);
    }

    // ==================== publishUnfrozen ====================
    /** 解冻：FROZEN → ACTIVE */
    public void publishUnfrozen(Merchant merchant) {
        MerchantEvent event = MerchantEvent.builder()
            .merchantId(merchant.getId())
            .userId(merchant.getUserId())
            .shopName(merchant.getShopName())
            .status(merchant.getStatus())       // = 1 (ACTIVE)
            .build();
        send(ROUTING_UNFROZEN, event);
        log.info("商家解冻事件已发布: merchantId={}", merchant.getId());
    }

    // ==================== publishClosed ====================
    /** 注销：ACTIVE → CLOSED */
    public void publishClosed(Merchant merchant) {
        MerchantEvent event = MerchantEvent.builder()
            .merchantId(merchant.getId())
            .userId(merchant.getUserId())
            .shopName(merchant.getShopName())
            .status(merchant.getStatus())       // = 4 (CLOSED)
            .build();
        send(ROUTING_CLOSED, event);
        log.warn("商家注销事件已发布: merchantId={}", merchant.getId());
    }


    // ==================== 底层发送 ====================
    private void send(String routingKey, MerchantEvent event) {
        try {
            rabbitTemplate.convertAndSend(MERCHANT_EXCHANGE, routingKey, event);
            log.debug("事件发送成功: exchange={}, routingKey={}, payload={}", MERCHANT_EXCHANGE, routingKey, event);
        } catch (Exception e) {
            log.error("事件发送失败: exchange={}, routingKey={}, merchantId={}",
                MERCHANT_EXCHANGE, routingKey, event.getMerchantId(), e);
            // 不向上抛异常，避免影响主业务流程（审核/冻结操作本身已落库）
        }
    }
}
