package com.ecommerce.csservice.dto;

import com.ecommerce.csservice.entity.Ticket;
import com.ecommerce.csservice.entity.TicketMessage;
import lombok.Data;

import java.util.List;

/**
 * 工单详情 VO
 */
@Data
public class TicketVO {

    private Ticket ticket;

    private List<TicketMessage> messages;

    private List<AuditLogVO> auditLogs;
}
