package com.ecommerce.client;

import com.ecommerce.dto.product.ProductAuditRequest;
import com.ecommerce.dto.product.ProductSummaryDTO;
import com.ecommerce.dto.product.SkuDetailDTO;
import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "product-service")
public interface ProductClient {

    /** 按商家ID + 审核状态统计商品数 */
    @GetMapping("/internal/products/count")
    Result<Long> countByMerchantAndAuditStatus(@RequestParam(required = false) Long merchantId,
                                                @RequestParam(required = false) Integer auditStatus);

    /** 查询 SKU 详情（含商品名、图片、商家 ID），供 cart-service 加购时获取快照 */
    @GetMapping("/internal/skus/{skuId}")
    Result<SkuDetailDTO> getSkuDetail(@PathVariable Long skuId);

    /** 按 ID 拉取商品摘要数据（用于 ES 索引同步等内部场景） */
    @GetMapping("/internal/products/{id}")
    Result<ProductSummaryDTO> getById(@PathVariable Long id);

    /** 批量按 ID 拉取商品摘要（供 recommendation-service 等回填商品信息） */
    @GetMapping("/internal/products/batch")
    Result<Map<Long, ProductSummaryDTO>> getProductsByIds(@RequestParam List<Long> ids);

    /** 商品审核（admin BFF 代理） */
    @PostMapping("/internal/products/{id}/audit")
    Result<Void> audit(@PathVariable Long id,
                        @RequestParam Long operatorId,
                        @RequestBody ProductAuditRequest req);
}
