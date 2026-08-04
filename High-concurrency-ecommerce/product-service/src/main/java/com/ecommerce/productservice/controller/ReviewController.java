package com.ecommerce.productservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.productservice.dto.ReviewResponse;
import com.ecommerce.productservice.dto.ReviewSubmitRequest;
import com.ecommerce.productservice.dto.ReviewSummaryResponse;
import com.ecommerce.productservice.service.ReviewService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 评价列表（公开） */
    @GetMapping("/api/products/{productId}/reviews")
    public Result<IPage<ReviewResponse>> pageByProduct(
        @PathVariable Long productId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Integer score) {
        return Result.success(reviewService.pageByProduct(productId, page, size, score));
    }

    /** 评价汇总（公开，缓存 5min） */
    @GetMapping("/api/products/{productId}/review-summary")
    public Result<ReviewSummaryResponse> getSummary(@PathVariable Long productId) {
        return Result.success(reviewService.getSummary(productId));
    }

    /** 提交评价（登录即可） */
    @PostMapping("/api/products/{productId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public Result<ReviewResponse> submit(@PathVariable Long productId,
                                         @RequestBody @Valid ReviewSubmitRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(reviewService.submit(productId, request, userId));
    }
}
