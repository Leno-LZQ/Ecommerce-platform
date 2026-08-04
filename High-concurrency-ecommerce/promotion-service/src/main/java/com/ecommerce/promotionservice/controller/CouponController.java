package com.ecommerce.promotionservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.promotionservice.dto.CouponTemplateVO;
import com.ecommerce.promotionservice.dto.CouponUserVO;
import com.ecommerce.promotionservice.service.CouponService;
import com.ecommerce.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C 端优惠券接口。
 */
@RestController
@RequestMapping("/api/promotion")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * 可领券列表。
     */
    @GetMapping("/coupons/available")
    public Result<List<CouponTemplateVO>> getAvailableTemplates() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(couponService.getAvailableTemplates(userId));
    }

    /**
     * 领取优惠券。
     */
    @PostMapping("/coupons/{templateId}/receive")
    public Result<CouponUserVO> receiveCoupon(@PathVariable Long templateId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(couponService.receiveCoupon(userId, templateId));
    }

    /**
     * 我的优惠券。
     */
    @GetMapping("/coupons/my")
    public Result<List<CouponUserVO>> getMyCoupons(
            @RequestParam(required = false) Integer status) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(couponService.getMyCoupons(userId, status));
    }
}
