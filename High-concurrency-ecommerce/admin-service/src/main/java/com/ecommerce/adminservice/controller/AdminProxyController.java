package com.ecommerce.adminservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.client.MerchantClient;
import com.ecommerce.client.ProductClient;
import com.ecommerce.dto.merchant.MerchantAuditRequest;
import com.ecommerce.dto.merchant.MerchantSummaryDTO;
import com.ecommerce.dto.product.ProductAuditRequest;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台操作代理。
 * 所有写入类操作（审核、配置）不在此做业务处理，直接透传到下游业务服务。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminProxyController {

    private final MerchantClient merchantClient;
    private final ProductClient  productClient;

    // ══════════════════════════════════════════
    //  商家审核
    // ══════════════════════════════════════════

    /** 商家审核 → merchant-service /internal/merchants/{id}/audit */
    @PostMapping("/merchants/{id}/audit")
    public Result<MerchantSummaryDTO> auditMerchant(@PathVariable Long id,
                                                     @Valid @RequestBody MerchantAuditRequest req) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return merchantClient.audit(id, operatorId, req);
    }

    /** 商家冻结 → merchant-service /internal/merchants/{id}/freeze */
    @PutMapping("/merchants/{id}/freeze")
    public Result<MerchantSummaryDTO> freezeMerchant(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "") String reason) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return merchantClient.freeze(id, operatorId, reason);
    }

    // ══════════════════════════════════════════
    //  商品审核
    // ══════════════════════════════════════════

    /** 商品审核 → product-service /internal/products/{id}/audit */
    @PostMapping("/products/{id}/audit")
    public Result<Void> auditProduct(@PathVariable Long id,
                                      @Valid @RequestBody ProductAuditRequest req) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return productClient.audit(id, operatorId, req);
    }

    // ══════════════════════════════════════════
    //  优惠管理（待完善）
    // ══════════════════════════════════════════
    // TODO: 创建 PromotionAdminClient，映射 promotion-service 的
    //       PromotionAdminController（coupon-templates / activities / seckill CRUD）
    //       当前 promotion-service 的管理端接口在 /api/admin/promotion/** 路径下

    // ══════════════════════════════════════════
    //  推荐策略（待完善）
    // ══════════════════════════════════════════
    // TODO: recommendation-service 尚未实现，暂时返回 503

    @GetMapping("/recommend/strategy")
    public Result<String> getRecommendStrategy() {
        return Result.error(503, "recommendation-service 尚未实现");
    }

    @PutMapping("/recommend/strategy")
    public Result<String> updateRecommendStrategy() {
        return Result.error(503, "recommendation-service 尚未实现");
    }
}
