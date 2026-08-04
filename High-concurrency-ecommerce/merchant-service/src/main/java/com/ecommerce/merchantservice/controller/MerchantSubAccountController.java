package com.ecommerce.merchantservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.merchantservice.dto.MerchantVO;
import com.ecommerce.merchantservice.dto.SubAccountAddRequest;
import com.ecommerce.merchantservice.dto.SubAccountVO;
import com.ecommerce.merchantservice.service.MerchantService;
import com.ecommerce.merchantservice.service.MerchantSubAccountService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/merchant/sub-accounts")
@RequiredArgsConstructor
public class MerchantSubAccountController {

    private final MerchantSubAccountService subAccountService;
    private final MerchantService merchantService;

    private Long getMerchantId() {
        Long userId = SecurityUtils.getCurrentUserId();
        return merchantService.getByUserId(userId).getId();
    }

    @GetMapping
    public Result<List<SubAccountVO>> list() {
        return Result.success(subAccountService.listByMerchant(getMerchantId()));
    }

    @PostMapping
    public Result<SubAccountVO> add(@Valid @RequestBody SubAccountAddRequest req) {
        return Result.success(subAccountService.add(getMerchantId(), req));
    }

    @PutMapping("/{id}/role")
    public Result<SubAccountVO> updateRole(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        return Result.success(subAccountService.updateRole(id, body.get("role")));
    }

    @PutMapping("/{id}/status")
    public Result<SubAccountVO> updateStatus(@PathVariable Long id,
                                              @RequestBody Map<String, Integer> body) {
        return Result.success(subAccountService.updateStatus(id, body.get("status")));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        subAccountService.remove(id);
        return Result.success();
    }
}
