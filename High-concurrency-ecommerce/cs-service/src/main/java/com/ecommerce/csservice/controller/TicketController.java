package com.ecommerce.csservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.csservice.dto.CreateTicketRequest;
import com.ecommerce.csservice.dto.ResolveConfirmRequest;
import com.ecommerce.csservice.dto.SendTicketMessageRequest;
import com.ecommerce.csservice.dto.TicketVO;
import com.ecommerce.csservice.entity.Ticket;
import com.ecommerce.csservice.entity.TicketMessage;
import com.ecommerce.csservice.service.TicketMessageService;
import com.ecommerce.csservice.service.TicketService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端客服工单接口
 */
@RestController
@RequestMapping("/api/cs/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketMessageService ticketMessageService;

    public TicketController(TicketService ticketService,
                            TicketMessageService ticketMessageService) {
        this.ticketService = ticketService;
        this.ticketMessageService = ticketMessageService;
    }

    @PostMapping
    public Result<Ticket> create(@Valid @RequestBody CreateTicketRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ticketService.createTicket(userId, request));
    }

    @GetMapping
    public Result<List<Ticket>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ticketService.listMyTickets(userId));
    }

    @GetMapping("/{id}")
    public Result<TicketVO> detail(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ticketService.getTicketDetail(id, userId));
    }

    @PostMapping("/{id}/messages")
    public Result<TicketMessage> sendMessage(@PathVariable Long id,
                                             @Valid @RequestBody SendTicketMessageRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ticketMessageService.sendByUser(id, userId, request));
    }

    @PostMapping("/{id}/resolve-confirm")
    public Result<Void> resolveConfirm(@PathVariable Long id,
                                       @Valid @RequestBody ResolveConfirmRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        ticketService.resolveConfirm(id, userId, request);
        return Result.success();
    }
}
