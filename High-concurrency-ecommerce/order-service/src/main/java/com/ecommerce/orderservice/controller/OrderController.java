package com.ecommerce.orderservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.CreateOrderResponse;
import com.ecommerce.orderservice.dto.OrderPageQuery;
import com.ecommerce.orderservice.dto.OrderVO;
import com.ecommerce.orderservice.service.OrderService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建订单 */
    @PostMapping
    public Result<CreateOrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        return Result.success(orderService.createOrder(req));
    }

    /** 订单详情 */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.success(orderService.getOrderDetail(orderNo));
    }

    /** 分页查询订单 */
    @GetMapping
    public Result<Page<OrderVO>> page(OrderPageQuery query) {
        return Result.success(orderService.pageQuery(query));
    }

    /** 取消订单 */
    @PutMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo, @RequestBody Map<String, String> body) {
        orderService.cancelOrder(orderNo, body.getOrDefault("reason", "用户取消"));
        return Result.success();
    }

    /** 发货（商家端，操作子订单） */
    @PutMapping("/split/{subOrderNo}/ship")
    public Result<Void> ship(@PathVariable String subOrderNo) {
        orderService.shipOrder(subOrderNo);
        return Result.success();
    }

    /** 退款 */
    @PutMapping("/{orderNo}/refund")
    public Result<Void> refund(@PathVariable String orderNo) {
        orderService.refundOrder(orderNo);
        return Result.success();
    }
}
