package com.ecommerce.csservice.event;

import com.ecommerce.csservice.config.RabbitConfig;
import com.ecommerce.csservice.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 客服事件消费者
 */
@Slf4j
@Component
public class CsEventConsumer {

    private final TicketService ticketService;

    public CsEventConsumer(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * 消费 order.refunded：自动关闭关联的纠纷/退款工单
     */
    @RabbitListener(queues = RabbitConfig.CS_QUEUE)
    public void onOrderRefunded(Map<String, Object> event) {
        String routingKey = (String) event.get("routingKey");
        if (!"order.refunded".equals(routingKey)) {
            return;
        }

        Long orderId = event.get("orderId") == null ? null : Long.valueOf(event.get("orderId").toString());
        if (orderId == null) {
            log.warn("收到 order.refunded 事件但缺少 orderId");
            return;
        }

        log.info("收到 order.refunded 事件, orderId={}, 自动关闭关联纠纷工单", orderId);
        ticketService.closeDisputeOnRefund(orderId);
    }
}
