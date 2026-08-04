package com.ecommerce.recommendationservice.controller;

import com.ecommerce.recommendationservice.dto.UserBehaviorRequest;
import com.ecommerce.recommendationservice.service.UserBehaviorService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 内部接口（供 gateway/其他服务调用）。
 */
@RestController
@RequestMapping("/internal/recommend")
@RequiredArgsConstructor
public class RecommendationInternalController {

    private final UserBehaviorService userBehaviorService;

    @PostMapping("/behavior")
    public Result<Void> behavior(@Valid @RequestBody UserBehaviorRequest request) {
        userBehaviorService.record(request);
        return Result.success();
    }

    @PostMapping("/{userId}/exclude/{productId}")
    public Result<Void> exclude(@PathVariable Long userId, @PathVariable Long productId) {
        userBehaviorService.addExclude(userId, productId);
        return Result.success();
    }

}
