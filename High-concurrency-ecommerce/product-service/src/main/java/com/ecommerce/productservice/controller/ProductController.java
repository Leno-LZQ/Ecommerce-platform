package com.ecommerce.productservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.productservice.dto.*;
import com.ecommerce.productservice.service.ProductService;
import com.ecommerce.result.Result;
import com.ecommerce.Security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Result<IPage<ProductListResponse>> pageQuery(ProductPageQuery query) {
        return Result.success(productService.pageQuery(query));
    }

    @GetMapping("/{id}")
    public Result<ProductResponse> getById(@PathVariable Long id) {
        // 走多级缓存
        return Result.success(productService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:create')")
    public Result<ProductResponse> create(@RequestBody @Valid ProductCreateRequest request) {
        Long merchantId = SecurityUtils.getCurrentMerchantId();
        return Result.success(productService.create(request,merchantId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:update')")
    public Result<ProductResponse> update(@PathVariable Long id,
                                          @RequestBody @Valid ProductUpdateRequest request) {
        Long merchantId = SecurityUtils.getCurrentMerchantId();
        return Result.success(productService.update(id, request, merchantId));
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('product:audit')")
    public Result<Void> audit(@PathVariable Long id,
                              @RequestBody @Valid AuditRequest request) {
        Long adminId = SecurityUtils.getCurrentUserId();
        productService.audit(id, request, adminId);
        return Result.success(null);
    }

}
