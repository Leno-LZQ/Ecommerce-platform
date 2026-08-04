package com.ecommerce.paymentservice.controller;

import com.ecommerce.dto.payment.PayCallbackRequest;
import com.ecommerce.dto.payment.PayCloseRequest;
import com.ecommerce.dto.payment.PayCreateRequest;
import com.ecommerce.paymentservice.service.PaymentService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 创建支付单（内部接口，order-service Saga 调用） */
    @PostMapping("/internal/payment/create")
    public Result<String> create(@Valid @RequestBody PayCreateRequest request) {
        return Result.success(paymentService.create(
                request.getOrderNo(), request.getUserId(), request.getPayAmount()));
    }

    /** 关闭支付单（内部接口，Saga 补偿调用） */
    @PostMapping("/internal/payment/close")
    public Result<Void> close(@Valid @RequestBody PayCloseRequest request) {
        paymentService.close(request.getPayNo());
        return Result.success();
    }

    /** 模拟支付回调（测试用） */
    @PostMapping("/internal/payment/callback")
    public Result<Void> callback(@Valid @RequestBody PayCallbackRequest request) {
        paymentService.callback(request.getPayNo(),
                "true".equalsIgnoreCase(request.getSuccess()));
        return Result.success();
    }

    /** 今日交易额（admin 看板） */
    @GetMapping("/internal/payment/today-revenue")
    public Result<BigDecimal> getTodayRevenue() {
        return Result.success(paymentService.getTodayRevenue());
    }
}
