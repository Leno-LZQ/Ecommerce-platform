package com.ecommerce.csservice.event;

import com.ecommerce.csservice.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 客服事件发布者
 */
@Slf4j
@Component
public class CsEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public CsEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTicketCreated(Long ticketId, Long userId, String category) {
        Map<String, Object> event = buildBaseEvent(ticketId, userId);
        event.put("category", category);
        send("cs.ticket.created", event);
    }

    public void publishTicketAssigned(Long ticketId, Long agentId, Long userId) {
        Map<String, Object> event = buildBaseEvent(ticketId, userId);
        event.put("agentId", agentId);
        send("cs.ticket.assigned", event);
    }

    public void publishTicketResolved(Long ticketId, Long userId) {
        Map<String, Object> event = buildBaseEvent(ticketId, userId);
        send("cs.ticket.resolved", event);
    }

    public void publishTicketOverdue(Long ticketId, Long userId, String slaType) {
        Map<String, Object> event = buildBaseEvent(ticketId, userId);
        event.put("slaType", slaType);
        send("cs.ticket.overdue", event);
    }

    private Map<String, Object> buildBaseEvent(Long ticketId, Long userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("ticketId", ticketId);
        event.put("userId", userId);
        event.put("timestamp", System.currentTimeMillis());
        return event;
    }

    private void send(String routingKey, Map<String, Object> event) {
        rabbitTemplate.convertAndSend(RabbitConfig.CS_EXCHANGE, routingKey, event);
        log.debug("发布客服事件: routingKey={}, ticketId={}", routingKey, event.get("ticketId"));
    }
}
