package com.ecommerce.productservice.controller;

import com.ecommerce.dto.product.ProductAuditRequest;
import com.ecommerce.dto.product.ProductSummaryDTO;
import com.ecommerce.dto.product.SkuDetailDTO;
import com.ecommerce.productservice.dto.AuditRequest;
import com.ecommerce.productservice.service.ProductService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内部接口 —— 供 cart-service / merchant-service / admin-service / search-service 等微服务通过 Feign 调用。
 * Gateway 应屏蔽 /internal/**，不暴露给公网。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class SkuInternalController {

    private final ProductService productService;

    /** 按 SKU ID 查询详情（含商品名、图片、商家 ID），供加购快照使用 */
    @GetMapping("/skus/{skuId}")
    public Result<SkuDetailDTO> getSkuDetail(@PathVariable Long skuId) {
        return Result.success(productService.getSkuDetail(skuId));
    }

    /** 按 ID 查询商品摘要（供 search-service ES 索引同步等内部场景） */
    @GetMapping("/products/{id}")
    public Result<ProductSummaryDTO> getSummaryById(@PathVariable Long id) {
        return Result.success(productService.getSummaryById(id));
    }

    /** 批量按 ID 拉取商品摘要（供 recommendation-service 等回填商品信息） */
    @GetMapping("/products/batch")
    public Result<Map<Long, ProductSummaryDTO>> getProductsByIds(@RequestParam List<Long> ids) {
        Map<Long, ProductSummaryDTO> map = ids.stream()
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> productService.getSummaryById(id),
                        (a, b) -> a
                ));
        return Result.success(map);
    }

    /** 按商家ID + 审核状态统计商品数（供 merchant-service / admin-service Feign 调用） */
    @GetMapping("/products/count")
    public Result<Long> countByMerchantAndAuditStatus(@RequestParam(required = false) Long merchantId,
                                                       @RequestParam(required = false) Integer auditStatus) {
        return Result.success(productService.countByMerchantAndAuditStatus(merchantId, auditStatus));
    }

    /** 商品审核（admin BFF 代理） */
    @PostMapping("/products/{id}/audit")
    public Result<Void> audit(@PathVariable Long id,
                               @RequestParam Long operatorId,
                               @Valid @RequestBody ProductAuditRequest req) {
        AuditRequest localReq = new AuditRequest();
        localReq.setApproved(req.getApproved());
        localReq.setRemark(req.getRemark());
        productService.audit(id, localReq, operatorId);
        return Result.success();
    }
}
