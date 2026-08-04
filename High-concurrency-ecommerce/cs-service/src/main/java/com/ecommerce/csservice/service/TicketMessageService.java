package com.ecommerce.csservice.service;

import com.ecommerce.constant.ErrorCode;
import com.ecommerce.csservice.dto.SendTicketMessageRequest;
import com.ecommerce.csservice.entity.CsAgent;
import com.ecommerce.csservice.entity.Ticket;
import com.ecommerce.csservice.entity.TicketMessage;
import com.ecommerce.csservice.entity.enums.SenderType;
import com.ecommerce.csservice.entity.enums.TicketStatus;
import com.ecommerce.csservice.mapper.TicketMapper;
import com.ecommerce.csservice.mapper.TicketMessageMapper;
import com.ecommerce.csservice.websocket.CsSessionManager;
import com.ecommerce.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 工单消息服务
 */
@Slf4j
@Service
public class TicketMessageService {

    private final TicketMapper ticketMapper;
    private final TicketMessageMapper ticketMessageMapper;
    private final AgentService agentService;
    private final CsPushService csPushService;

    public TicketMessageService(TicketMapper ticketMapper,
                                TicketMessageMapper ticketMessageMapper,
                                AgentService agentService,
                                CsPushService csPushService) {
        this.ticketMapper = ticketMapper;
        this.ticketMessageMapper = ticketMessageMapper;
        this.agentService = agentService;
        this.csPushService = csPushService;
    }

    /**
     * 用户发送消息
     */
    @Transactional
    public TicketMessage sendByUser(Long ticketId, Long userId, SendTicketMessageRequest request) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null || !ticket.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        if (TicketStatus.CLOSED.getCode().equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        TicketMessage message = saveMessage(ticketId, SenderType.USER, userId, request);

        // 推送给分配的客服
        if (ticket.getAssignedAgentId() != null) {
            csPushService.pushToAgent(ticket.getAssignedAgentId(), message);
        }

        return message;
    }

    /**
     * 客服发送消息
     */
    @Transactional
    public TicketMessage sendByAgent(Long ticketId, Long agentUserId, SendTicketMessageRequest request) {
        CsAgent agent = agentService.getByUserId(agentUserId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        if (!agent.getId().equals(ticket.getAssignedAgentId())) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        // 如果是 ASSIGNED 状态，自动推进到 IN_PROGRESS
        if (TicketStatus.ASSIGNED.getCode().equals(ticket.getStatus())) {
            ticket.setStatus(TicketStatus.IN_PROGRESS.getCode());
            ticket.setUpdateTime(LocalDateTime.now());
            ticketMapper.updateById(ticket);
        }

        TicketMessage message = saveMessage(ticketId, SenderType.AGENT, agentUserId, request);

        // 推送给用户
        csPushService.pushToUser(ticket.getUserId(), message);

        return message;
    }

    private TicketMessage saveMessage(Long ticketId, SenderType senderType,
                                      Long senderId, SendTicketMessageRequest request) {
        TicketMessage message = new TicketMessage();
        message.setTicketId(ticketId);
        message.setSenderType(senderType.getCode());
        message.setSenderId(senderId);
        message.setContent(request.getContent());
        message.setAttachmentUrls(request.getAttachmentUrls());
        message.setCreateTime(LocalDateTime.now());
        ticketMessageMapper.insert(message);
        return message;
    }
}
