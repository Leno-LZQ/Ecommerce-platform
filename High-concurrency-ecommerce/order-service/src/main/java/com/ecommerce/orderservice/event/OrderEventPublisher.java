package com.ecommerce.orderservice.event;

import com.ecommerce.orderservice.entity.Orders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    private static final String ORDER_EXCHANGE = "order.exchange";

    /** 订单创建 */
    public void publishCreated(Orders orders) {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderNo(orders.getOrderNo())
                .userId(orders.getUserId())
                .totalAmount(orders.getTotalAmount())
                .payAmount(orders.getPayAmount())
                .consignee(orders.getConsignee())
                .phone(orders.getPhone())
                .address(orders.getAddress())
                .build();
        send("order.created", event);
    }

    /** 支付成功 */
    public void publishPaid(Orders orders, Long merchantId) {
        OrderPaidEvent event = OrderPaidEvent.builder()
                .orderId(orders.getId())
                .merchantId(merchantId)
                .orderNo(orders.getOrderNo())
                .userId(orders.getUserId())
                .payAmount(orders.getPayAmount())
                .payType(orders.getPayType())
                .build();
        send("order.paid", event);
    }

    /** 订单取消 */
    public void publishCancelled(Orders orders) {
        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .orderNo(orders.getOrderNo())
                .userId(orders.getUserId())
                .cancelReason(orders.getCancelReason())
                .build();
        send("order.cancelled", event);
    }

    /** 已发货 */
    public void publishShipped(String orderNo) {
        OrderShippedEvent event = OrderShippedEvent.builder()
                .orderNo(orderNo)
                .build();
        send("order.shipped", event);
    }

    /** 已退款 */
    public void publishRefunded(String orderNo, BigDecimal refundAmount) {
        OrderRefundedEvent event = OrderRefundedEvent.builder()
                .orderNo(orderNo)
                .refundAmount(refundAmount)
                .build();
        send("order.refunded", event);
    }

    private void send(String routingKey, Object event) {
        rabbitTemplate.convertAndSend(ORDER_EXCHANGE, routingKey, event);
        log.debug("发布事件: routingKey={}, event={}", routingKey, event);
    }
}
