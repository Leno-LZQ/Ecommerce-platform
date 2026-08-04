package com.ecommerce.client;

import com.ecommerce.dto.order.OrderDTO;
import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "order-service")
public interface OrderClient {

    @GetMapping("/internal/orders/{orderNo}")
    Result<OrderDTO> getOrderByNo(@PathVariable String orderNo);

    /** 支付成功回调后标记订单已支付（payment-service 调用） */
    @PostMapping("/internal/orders/{orderNo}/paid")
    Result<Void> markPaid(@PathVariable String orderNo);

    /**
     * 按商家ID和订单状态统计子订单数量（merchantId 可选，null=全局）。
     * @param merchantId 商家ID（可选）
     * @param status     订单状态：PAID / SHIPPED / COMPLETED 等（可选）
     */
    @GetMapping("/internal/orders/count")
    Result<Long> countByMerchantAndStatus(@RequestParam(required = false) Long merchantId,
                                          @RequestParam(required = false) String status);

    /**
     * 主订单维度全局统计（按状态，status 可选，null=全部）。
     * @param status 订单状态：0=待支付 1=已支付 2=已发货 3=已完成 4=已取消 5=已退款（可选）
     */
    @GetMapping("/internal/orders/count-by-status")
    Result<Long> countByStatus(@RequestParam(required = false) Integer status);
}
