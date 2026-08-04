package com.ecommerce.client;

import com.ecommerce.dto.payment.PayCallbackRequest;
import com.ecommerce.dto.payment.PayCloseRequest;
import com.ecommerce.dto.payment.PayCreateRequest;
import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    // ══════════════════════════════════════════
    //  对接 PaymentController
    // ══════════════════════════════════════════

    /** 创建支付单，返回 payNo */
    @PostMapping("/internal/payment/create")
    Result<String> create(@RequestBody PayCreateRequest request);

    /** 关闭支付单（取消/超时/补偿） */
    @PostMapping("/internal/payment/close")
    Result<Void> close(@RequestBody PayCloseRequest request);

    /** 模拟支付回调（测试用） */
    @PostMapping("/internal/payment/callback")
    Result<Void> callback(@RequestBody PayCallbackRequest request);

    /** 今日交易额（admin 看板） */
    @GetMapping("/internal/payment/today-revenue")
    Result<BigDecimal> getTodayRevenue();
}
