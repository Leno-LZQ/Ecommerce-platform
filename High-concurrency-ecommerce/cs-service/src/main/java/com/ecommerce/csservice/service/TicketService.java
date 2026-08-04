package com.ecommerce.csservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.csservice.dto.AuditLogVO;
import com.ecommerce.csservice.dto.CreateTicketRequest;
import com.ecommerce.csservice.dto.ResolveConfirmRequest;
import com.ecommerce.csservice.dto.TicketVO;
import com.ecommerce.csservice.dto.TransferRequest;
import com.ecommerce.csservice.entity.CsAgent;
import com.ecommerce.csservice.entity.Ticket;
import com.ecommerce.csservice.entity.TicketAuditLog;
import com.ecommerce.csservice.entity.TicketMessage;
import com.ecommerce.csservice.entity.enums.SenderType;
import com.ecommerce.csservice.entity.enums.TicketCategory;
import com.ecommerce.csservice.entity.enums.TicketStatus;
import com.ecommerce.csservice.event.CsEventPublisher;
import com.ecommerce.csservice.mapper.TicketAuditLogMapper;
import com.ecommerce.csservice.mapper.TicketMapper;
import com.ecommerce.csservice.mapper.TicketMessageMapper;
import com.ecommerce.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工单核心服务
 *
 * <p>负责工单的创建、状态机流转、分配、转接、解决、关闭。</p>
 */
@Slf4j
@Service
public class TicketService {

    private final TicketMapper ticketMapper;
    private final TicketMessageMapper ticketMessageMapper;
    private final TicketAuditLogMapper ticketAuditLogMapper;
    private final AgentService agentService;
    private final TicketRateLimitService rateLimitService;
    private final CsEventPublisher csEventPublisher;

    public TicketService(TicketMapper ticketMapper,
                         TicketMessageMapper ticketMessageMapper,
                         TicketAuditLogMapper ticketAuditLogMapper,
                         AgentService agentService,
                         TicketRateLimitService rateLimitService,
                         CsEventPublisher csEventPublisher) {
        this.ticketMapper = ticketMapper;
        this.ticketMessageMapper = ticketMessageMapper;
        this.ticketAuditLogMapper = ticketAuditLogMapper;
        this.agentService = agentService;
        this.rateLimitService = rateLimitService;
        this.csEventPublisher = csEventPublisher;
    }

    /**
     * 用户创建工单
     */
    @Transactional
    public Ticket createTicket(Long userId, CreateTicketRequest request) {
        // 1. 限流：同一用户 1 小时内最多 5 个工单
        if (!rateLimitService.tryCreateTicket(userId)) {
            throw new BusinessException(ErrorCode.TICKET_LIMIT_EXCEEDED);
        }

        // 2. 校验分类
        TicketCategory category = TicketCategory.of(request.getCategory());
        if (category == null) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        // 3. 创建工单
        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setUserId(userId);
        ticket.setCategory(request.getCategory());
        ticket.setPriority(request.getPriority());
        ticket.setStatus(TicketStatus.CREATED.getCode());
        ticket.setTitle(request.getTitle());
        ticket.setOrderId(request.getOrderId());
        ticket.setCreateTime(LocalDateTime.now());
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.insert(ticket);

        // 4. 写入首条用户消息
        TicketMessage firstMsg = new TicketMessage();
        firstMsg.setTicketId(ticket.getId());
        firstMsg.setSenderType(SenderType.USER.getCode());
        firstMsg.setSenderId(userId);
        firstMsg.setContent(request.getContent());
        firstMsg.setCreateTime(LocalDateTime.now());
        ticketMessageMapper.insert(firstMsg);

        // 5. 审计日志
        saveAuditLog(ticket.getId(), userId, "CREATE",
            "用户创建工单，分类=" + category.getDesc());

        // 6. 发布事件
        csEventPublisher.publishTicketCreated(ticket.getId(), ticket.getUserId(), ticket.getCategory());

        log.info("工单创建成功: ticketNo={}, userId={}", ticket.getTicketNo(), userId);
        return ticket;
    }

    /**
     * 用户查询我的工单列表
     */
    public List<Ticket> listMyTickets(Long userId) {
        return ticketMapper.findByUserId(userId);
    }

    /**
     * 客服查询工单池（待分配 + 我的工单）
     */
    public List<Ticket> listAgentPool(Long agentUserId) {
        CsAgent agent = getAgentOrThrow(agentUserId);
        return ticketMapper.findAgentPool(agent.getId());
    }

    /**
     * 工单详情（含消息历史和审计日志）
     */
    public TicketVO getTicketDetail(Long ticketId, Long userId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        // 只有工单所属用户或分配的客服/主管能查看
        if (!ticket.getUserId().equals(userId)) {
            CsAgent agent = agentService.getByUserId(userId);
            if (agent == null || (!agent.getId().equals(ticket.getAssignedAgentId()) && !isManager(agent))) {
                throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
            }
        }

        TicketVO vo = new TicketVO();
        vo.setTicket(ticket);
        vo.setMessages(ticketMessageMapper.findByTicketId(ticketId));
        vo.setAuditLogs(ticketAuditLogMapper.findByTicketId(ticketId).stream()
            .map(log -> {
                AuditLogVO audit = new AuditLogVO();
                audit.setId(log.getId());
                audit.setOperatorId(log.getOperatorId());
                audit.setAction(log.getAction());
                audit.setDetail(log.getDetail());
                audit.setCreateTime(log.getCreateTime());
                return audit;
            }).toList());
        return vo;
    }

    /**
     * 客服认领工单（普通 AGENT 只能认领未分配的）
     */
    @Transactional
    public void assignTicket(Long ticketId, Long agentUserId) {
        CsAgent agent = getAgentOrThrow(agentUserId);
        Ticket ticket = getAndCheckTicket(ticketId);

        if (!TicketStatus.CREATED.getCode().equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        // 检查负载
        int activeCount = ticketMapper.countByAgentId(agent.getId());
        if (activeCount >= agent.getMaxConcurrent()) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        doAssign(ticket, agent.getId(), agentUserId, "客服认领工单");
    }

    /**
     * 主管手动分配 / 转接
     */
    @Transactional
    public void transferTicket(Long ticketId, Long operatorUserId, TransferRequest request) {
        CsAgent operator = getAgentOrThrow(operatorUserId);
        Ticket ticket = getAndCheckTicket(ticketId);

        // 转接需要主管权限，或者客服自己把工单转给其他人（需主管确认，这里简化：仅主管可操作）
        if (!isManager(operator)) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        CsAgent target = agentService.getById(request.getTargetAgentId());
        if (target == null) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        String oldAgent = ticket.getAssignedAgentId() == null ? "无" : String.valueOf(ticket.getAssignedAgentId());
        ticket.setStatus(TicketStatus.ASSIGNED.getCode());
        ticket.setAssignedAgentId(target.getId());
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        saveAuditLog(ticketId, operatorUserId, "TRANSFER",
            "从客服 " + oldAgent + " 转接给客服 " + target.getId() + "，原因：" + request.getReason());

        csEventPublisher.publishTicketAssigned(ticketId, target.getId(), ticket.getUserId());
    }

    /**
     * 客服标记已解决
     */
    @Transactional
    public void resolveTicket(Long ticketId, Long agentUserId, String resolutionNote) {
        CsAgent agent = getAgentOrThrow(agentUserId);
        Ticket ticket = getAndCheckTicket(ticketId);

        if (!agent.getId().equals(ticket.getAssignedAgentId())) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        if (!TicketStatus.IN_PROGRESS.getCode().equals(ticket.getStatus())
            && !TicketStatus.ASSIGNED.getCode().equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        ticket.setStatus(TicketStatus.RESOLVED.getCode());
        ticket.setResolutionNote(resolutionNote);
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        saveAuditLog(ticketId, agentUserId, "RESOLVE", "客服标记已解决：" + resolutionNote);
        csEventPublisher.publishTicketResolved(ticketId, ticket.getUserId());
    }

    /**
     * 用户确认解决 / 不满意重新打开
     */
    @Transactional
    public void resolveConfirm(Long ticketId, Long userId, ResolveConfirmRequest request) {
        Ticket ticket = getAndCheckTicket(ticketId);
        if (!ticket.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }

        if (request.getSatisfied()) {
            // 确认解决 → 关闭
            if (!TicketStatus.RESOLVED.getCode().equals(ticket.getStatus())) {
                throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
            }
            ticket.setStatus(TicketStatus.CLOSED.getCode());
            ticket.setClosedAt(LocalDateTime.now());
            ticket.setUpdateTime(LocalDateTime.now());
            ticketMapper.updateById(ticket);

            saveAuditLog(ticketId, userId, "CLOSE", "用户确认解决，关闭工单");
        } else {
            // 不满意重新打开：每天最多 3 次
            if (!rateLimitService.tryReopenTicket(ticketId, userId)) {
                throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
            }
            if (!TicketStatus.RESOLVED.getCode().equals(ticket.getStatus())) {
                throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
            }
            ticket.setStatus(TicketStatus.IN_PROGRESS.getCode());
            ticket.setUpdateTime(LocalDateTime.now());
            ticketMapper.updateById(ticket);

            saveAuditLog(ticketId, userId, "REOPEN", "用户不满意，重新打开工单");
        }
    }

    /**
     * 自动分配（创建工单后调用，也可由调度任务触发）
     */
    @Transactional
    public void autoAssign(Long ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null || !TicketStatus.CREATED.getCode().equals(ticket.getStatus())) {
            return;
        }

        CsAgent agent = agentService.findBestAgent(ticket.getCategory());
        if (agent == null) {
            log.warn("工单 {} 无可用客服，保持 CREATED 状态", ticketId);
            return;
        }

        doAssign(ticket, agent.getId(), null, "系统自动分配工单");
    }

    /**
     * 处理退款事件：自动关闭关联纠纷工单
     */
    @Transactional
    public void closeDisputeOnRefund(Long orderId) {
        Ticket ticket = ticketMapper.findOpenDisputeByOrderId(orderId);
        if (ticket == null) {
            return;
        }
        ticket.setStatus(TicketStatus.CLOSED.getCode());
        ticket.setClosedAt(LocalDateTime.now());
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        saveAuditLog(ticket.getId(), 0L, "CLOSE",
            "订单 " + orderId + " 已退款，系统自动关闭关联纠纷工单");

        csEventPublisher.publishTicketResolved(ticket.getId(), ticket.getUserId());
    }

    /**
     * 客服处理中：把 ASSIGNED 推进到 IN_PROGRESS
     */
    @Transactional
    public void startProgress(Long ticketId, Long agentUserId) {
        CsAgent agent = getAgentOrThrow(agentUserId);
        Ticket ticket = getAndCheckTicket(ticketId);
        if (!agent.getId().equals(ticket.getAssignedAgentId())) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }
        if (!TicketStatus.ASSIGNED.getCode().equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }
        ticket.setStatus(TicketStatus.IN_PROGRESS.getCode());
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        saveAuditLog(ticketId, agentUserId, "REPLY", "客服开始处理工单");
    }

    // ===================== 内部辅助方法 =====================

    private Ticket getAndCheckTicket(Long ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    private CsAgent getAgentOrThrow(Long userId) {
        CsAgent agent = agentService.getByUserId(userId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_ERROR);
        }
        return agent;
    }

    private boolean isManager(CsAgent agent) {
        return "MANAGER".equals(agent.getRole());
    }

    private void doAssign(Ticket ticket, Long agentId, Long operatorId, String detail) {
        ticket.setStatus(TicketStatus.ASSIGNED.getCode());
        ticket.setAssignedAgentId(agentId);
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        saveAuditLog(ticket.getId(), operatorId == null ? 0L : operatorId, "ASSIGN", detail);
        csEventPublisher.publishTicketAssigned(ticket.getId(), agentId, ticket.getUserId());
    }

    private void saveAuditLog(Long ticketId, Long operatorId, String action, String detail) {
        TicketAuditLog log = new TicketAuditLog();
        log.setTicketId(ticketId);
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setDetail(detail);
        log.setCreateTime(LocalDateTime.now());
        ticketAuditLogMapper.insert(log);
    }

    private String generateTicketNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "CS" + date + random;
    }
}
