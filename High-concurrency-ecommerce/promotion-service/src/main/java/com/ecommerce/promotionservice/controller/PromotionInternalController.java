package com.ecommerce.promotionservice.controller;

import com.ecommerce.dto.promotion.PromotionPreviewVO;
import com.ecommerce.dto.promotion.PreviewRequest;
import com.ecommerce.dto.promotion.PromotionLockRequest;
import com.ecommerce.dto.promotion.PromotionLockResponse;
import com.ecommerce.dto.promotion.RefundRequest;
import com.ecommerce.promotionservice.engine.CalcMode;
import com.ecommerce.promotionservice.engine.PromotionContext;
import com.ecommerce.promotionservice.service.DiscountEngine;
import com.ecommerce.promotionservice.service.PromotionLockService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内部 Feign 接口（供 order-service / cart-service 调用）。
 * /internal 前缀，网关不暴露。
 */
@RestController
@RequestMapping("/internal/promotion")
@RequiredArgsConstructor
public class PromotionInternalController {

    private final DiscountEngine discountEngine;
    private final PromotionLockService promotionLockService;

    /**
     * 购物车优惠预览（只读，无副作用）。
     */
    @PostMapping("/preview")
    public Result<List<PromotionPreviewVO>> preview(@RequestBody @Valid PreviewRequest req) {
        PromotionContext ctx = discountEngine.calculate(
            req.getUserId(), req.getCouponCodes(), req.getItems(), CalcMode.PREVIEW);
        return Result.success(discountEngine.preview(ctx));
    }

    /**
     * 下单优惠锁定。
     */
    @PostMapping("/lock")
    public Result<PromotionLockResponse> lock(@RequestBody @Valid PromotionLockRequest req) {
        return Result.success(promotionLockService.lock(req));
    }

    /**
     * 支付后核销。
     */
    @PostMapping("/confirm")
    public Result<Void> confirm(@RequestParam String orderNo) {
        promotionLockService.confirm(orderNo);
        return Result.success();
    }

    /**
     * 取消/超时释放。
     */
    @PostMapping("/release")
    public Result<Void> release(@RequestParam String orderNo) {
        promotionLockService.release(orderNo);
        return Result.success();
    }

    /**
     * 退款处理。
     */
    @PostMapping("/refund")
    public Result<Void> refund(@RequestBody @Valid RefundRequest req) {
        promotionLockService.refund(req);
        return Result.success();
    }
}
