package com.ecommerce.productservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.productservice.service.StockNotificationService;
import com.ecommerce.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/sku/{skuId}/notify")
@RequiredArgsConstructor
public class StockNotificationController {

    private final StockNotificationService stockNotificationService;

    @PostMapping
    public Result<Void> subscribe(@PathVariable Long skuId) {
        Long userId = SecurityUtils.getCurrentUserId();
        stockNotificationService.subscribe(skuId, userId);
        return Result.success();
    }

    @DeleteMapping
    public Result<Void> unsubscribe(@PathVariable Long skuId) {
        Long userId = SecurityUtils.getCurrentUserId();
        stockNotificationService.unsubscribe(skuId, userId);
        return Result.success();
    }
}
