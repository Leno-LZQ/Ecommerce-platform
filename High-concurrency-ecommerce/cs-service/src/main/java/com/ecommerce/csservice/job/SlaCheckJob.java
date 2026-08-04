package com.ecommerce.csservice.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.csservice.entity.Ticket;
import com.ecommerce.csservice.entity.enums.TicketStatus;
import com.ecommerce.csservice.event.CsEventPublisher;
import com.ecommerce.csservice.mapper.TicketMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SLA 超时检查定时任务
 *
 * <p>每 5 分钟扫描一次未解决的工单，检查是否超过首次响应 SLA。</p>
 * <p>简化策略：以创建时间 + SLA 时长为阈值，超时时发布 cs.ticket.overdue 事件。</p>
 */
@Slf4j
@Component
public class SlaCheckJob {

    /**
     * SLA 时长（小时）：紧急 2h，普通 4h，低 24h
     */
    private static final int[] SLA_HOURS = {0, 2, 4, 24};

    private final TicketMapper ticketMapper;
    private final CsEventPublisher csEventPublisher;

    public SlaCheckJob(TicketMapper ticketMapper, CsEventPublisher csEventPublisher) {
        this.ticketMapper = ticketMapper;
        this.csEventPublisher = csEventPublisher;
    }

    /**
     * 每 5 分钟执行一次
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void checkSla() {
        QueryWrapper<Ticket> wrapper = new QueryWrapper<>();
        wrapper.in("status", TicketStatus.CREATED.getCode(), TicketStatus.ASSIGNED.getCode(), TicketStatus.IN_PROGRESS.getCode());
        List<Ticket> tickets = ticketMapper.selectList(wrapper);

        LocalDateTime now = LocalDateTime.now();
        for (Ticket ticket : tickets) {
            int priority = ticket.getPriority() == null ? 2 : ticket.getPriority();
            int slaHours = SLA_HOURS[Math.max(1, Math.min(priority, 3))];
            LocalDateTime deadline = ticket.getCreateTime().plusHours(slaHours);

            if (now.isAfter(deadline)) {
                log.warn("工单 SLA 超时: ticketId={}, priority={}, deadline={}",
                    ticket.getId(), priority, deadline);
                csEventPublisher.publishTicketOverdue(ticket.getId(), ticket.getUserId(), "FIRST_RESPONSE");
            }
        }
    }
}
