package com.ecommerce.client;

import com.ecommerce.dto.promotion.PreviewRequest;
import com.ecommerce.dto.promotion.PromotionLockRequest;
import com.ecommerce.dto.promotion.PromotionLockResponse;
import com.ecommerce.dto.promotion.PromotionPreviewVO;
import com.ecommerce.dto.promotion.RefundRequest;
import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 优惠引擎 Feign 客户端（供 order-service / cart-service 调用）。
 */
@FeignClient(name = "promotion-service")
public interface PromotionClient {

    /**
     * 购物车优惠预览（只读，无副作用）。
     */
    @PostMapping("/internal/promotion/preview")
    Result<List<PromotionPreviewVO>> preview(@RequestBody PreviewRequest req);

    /**
     * 下单优惠锁定（Saga Step 2.5）。
     */
    @PostMapping("/internal/promotion/lock")
    Result<PromotionLockResponse> lock(@RequestBody PromotionLockRequest req);

    /**
     * 支付后核销。
     */
    @PostMapping("/internal/promotion/confirm")
    Result<Void> confirm(@RequestParam("orderNo") String orderNo);

    /**
     * 取消/超时释放。
     */
    @PostMapping("/internal/promotion/release")
    Result<Void> release(@RequestParam("orderNo") String orderNo);

    /**
     * 退款处理。
     */
    @PostMapping("/internal/promotion/refund")
    Result<Void> refund(@RequestBody RefundRequest req);
}
