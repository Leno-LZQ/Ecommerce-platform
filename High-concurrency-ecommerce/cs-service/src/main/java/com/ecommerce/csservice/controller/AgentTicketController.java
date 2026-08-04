package com.ecommerce.csservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.csservice.dto.SendTicketMessageRequest;
import com.ecommerce.csservice.dto.TicketVO;
import com.ecommerce.csservice.dto.TransferRequest;
import com.ecommerce.csservice.entity.Ticket;
import com.ecommerce.csservice.entity.TicketMessage;
import com.ecommerce.csservice.service.TicketMessageService;
import com.ecommerce.csservice.service.TicketService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客服端工单接口
 */
@RestController
@RequestMapping("/api/cs/agent/tickets")
public class AgentTicketController {

    private final TicketService ticketService;
    private final TicketMessageService ticketMessageService;

    public AgentTicketController(TicketService ticketService,
                                 TicketMessageService ticketMessageService) {
        this.ticketService = ticketService;
        this.ticketMessageService = ticketMessageService;
    }

    @GetMapping
    public Result<List<Ticket>> pool() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ticketService.listAgentPool(userId));
    }

    @PostMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        ticketService.assignTicket(id, userId);
        return Result.success();
    }

    @PostMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        ticketService.startProgress(id, userId);
        return Result.success();
    }

    @PostMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id,
                                 @Valid @RequestBody TransferRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        ticketService.transferTicket(id, userId, request);
        return Result.success();
    }

    @PostMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id,
                                @RequestParam String resolutionNote) {
        Long userId = SecurityUtils.getCurrentUserId();
        ticketService.resolveTicket(id, userId, resolutionNote);
        return Result.success();
    }

    @PostMapping("/{id}/messages")
    public Result<TicketMessage> sendMessage(@PathVariable Long id,
                                             @Valid @RequestBody SendTicketMessageRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ticketMessageService.sendByAgent(id, userId, request));
    }

    @GetMapping("/{id}")
    public Result<TicketVO> detail(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ticketService.getTicketDetail(id, userId));
    }
}
