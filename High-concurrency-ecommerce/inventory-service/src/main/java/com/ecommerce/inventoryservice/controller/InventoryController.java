package com.ecommerce.inventoryservice.controller;

import com.ecommerce.dto.inventory.ReserveRequest;
import com.ecommerce.dto.inventory.ReserveResult;
import com.ecommerce.inventoryservice.service.InventoryService;
import com.ecommerce.inventoryservice.service.StockSyncService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InventoryController {

    @Autowired private InventoryService inventoryService;
    @Autowired private StockSyncService stockSyncService;

    @GetMapping("/inventory/{skuId}")
    public Result<Integer> getStock(@PathVariable Long skuId) {
        return Result.success(inventoryService.getStock(skuId));
    }

    @PostMapping("/inventory/batch-query")
    public Result<Map<Long, Integer>> batchGetStock(@RequestBody List<Long> skuIds) {
        return Result.success(inventoryService.batchGetStock(skuIds));
    }

    @PostMapping("/inventory/reserve")
    public Result<ReserveResult> reserve(@Valid @RequestBody ReserveRequest request) {
        return Result.success(inventoryService.reserve(request));
    }

    @PostMapping("/inventory/confirm")
    public Result<Void> confirm(@RequestBody Map<String, String> body) {
        inventoryService.confirm(body.get("orderNo"));
        return Result.success();
    }

    @PostMapping("/inventory/release")
    public Result<Void> release(@RequestBody Map<String, String> body) {
        inventoryService.release(body.get("orderNo"));
        return Result.success();
    }

    @PostMapping("/inventory/rollback")
    public Result<Void> rollback(@RequestBody Map<String, String> body) {
        inventoryService.rollback(body.get("orderNo"));
        return Result.success();
    }

    /** 手动触发库存初始化（DB → Redis，缺失 key 补建） */
    @PostMapping("/inventory/init")
    public Result<Integer> initStock() {
        return Result.success(stockSyncService.initStockNow());
    }
}

