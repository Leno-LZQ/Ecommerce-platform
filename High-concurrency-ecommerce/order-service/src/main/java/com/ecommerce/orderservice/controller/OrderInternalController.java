package com.ecommerce.orderservice.controller;

import com.ecommerce.dto.order.OrderDTO;
import com.ecommerce.orderservice.service.OrderService;
import com.ecommerce.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 内部接口 —— 供 settlement-service / admin-service 等微服务通过 Feign 调用。
 * Gateway 应屏蔽 /internal/**，不暴露给公网。
 */
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class OrderInternalController {

    private final OrderService orderService;

    /** 按订单号查订单（供 settlement-service 校验订单） */
    @GetMapping("/{orderNo}")
    public Result<OrderDTO> getOrderByNo(@PathVariable String orderNo) {
        return Result.success(orderService.getOrderByNo(orderNo));
    }

    /** 支付成功回调：标记订单已支付（payment-service 调用） */
    @PostMapping("/{orderNo}/paid")
    public Result<Void> markPaid(@PathVariable String orderNo) {
        orderService.markPaid(orderNo);
        return Result.success();
    }
    /** 按商家ID + 订单状态统计子订单数（merchantId 可选，null=全局） */
    @GetMapping("/count")
    public Result<Long> countByMerchantAndStatus(@RequestParam(required = false) Long merchantId,
                                                  @RequestParam(required = false) String status) {
        return Result.success(orderService.countByMerchantAndStatus(merchantId, status));
    }

    /** 主订单维度全局统计（按状态，status 可选，null=全部） */
    @GetMapping("/count-by-status")
    public Result<Long> countByStatus(@RequestParam(required = false) Integer status) {
        return Result.success(orderService.countByStatus(status));
    }
}
