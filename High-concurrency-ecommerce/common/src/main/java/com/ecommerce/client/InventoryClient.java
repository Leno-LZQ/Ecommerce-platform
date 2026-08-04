package com.ecommerce.client;

import com.ecommerce.dto.inventory.ReserveRequest;
import com.ecommerce.dto.inventory.ReserveResult;
import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    /** 查询单个 SKU 库存 */
    @GetMapping("/internal/inventory/{skuId}")
    Result<Integer> getStock(@PathVariable Long skuId);

    /** 批量查询 SKU 库存 */
    @PostMapping("/internal/inventory/batch-query")
    Result<Map<Long, Integer>> batchGetStock(@RequestBody List<Long> skuIds);

    /** 预扣库存（Saga 下单校验） */
    @PostMapping("/internal/inventory/reserve")
    Result<ReserveResult> reserve(@RequestBody ReserveRequest request);

    /** 确认扣减（支付成功后） */
    @PostMapping("/internal/inventory/confirm")
    Result<Void> confirm(@RequestBody Map<String, String> body);

    /** 释放预扣（取消/超时） */
    @PostMapping("/internal/inventory/release")
    Result<Void> release(@RequestBody Map<String, String> body);

    /** 回滚已确认库存（退款） */
    @PostMapping("/internal/inventory/rollback")
    Result<Void> rollback(@RequestBody Map<String, String> body);

}
