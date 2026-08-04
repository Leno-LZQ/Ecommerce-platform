package com.ecommerce.merchantservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.client.OrderClient;
import com.ecommerce.client.ProductClient;
import com.ecommerce.client.SettlementClient;
import com.ecommerce.merchantservice.dto.*;
import com.ecommerce.merchantservice.service.MerchantService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final ProductClient productClient;
    private final SettlementClient settlementClient;
    private final OrderClient orderClient;

    /** 商家入驻 */
    @PostMapping("/register")
    public Result<MerchantVO> register(@Valid @RequestBody MerchantRegisterRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(merchantService.register(userId, req));
    }

    /** 我的店铺信息 */
    @GetMapping("/my")
    public Result<MerchantVO> getMyShop() {
        Long userId = SecurityUtils.getCurrentUserId();
        MerchantVO vo = merchantService.getByUserId(userId);
        vo.setAuditLogs(merchantService.getAuditLogs(vo.getId()));
        return Result.success(vo);
    }

    /** 更新店铺信息 */
    @PutMapping("/my")
    public Result<MerchantVO> updateShopInfo(@Valid @RequestBody MerchantUpdateRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        MerchantVO me = merchantService.getByUserId(userId);
        return Result.success(merchantService.updateShopInfo(me.getId(), req));
    }

    /** 商家仪表盘（红点聚合） */
    @GetMapping("/dashboard/summary")
    public Result<DashboardSummaryVO> getDashboardSummary() {
        Long userId = SecurityUtils.getCurrentUserId();
        MerchantVO me = merchantService.getByUserId(userId);
        Long merchantId = me.getId();

        CompletableFuture<Long> pendingProducts = CompletableFuture.supplyAsync(() -> {
            try {
                var r = productClient.countByMerchantAndAuditStatus(merchantId, 0);
                return r != null && r.getData() != null ? r.getData() : 0L;
            } catch (Exception e) { return 0L; }
        });

        CompletableFuture<Long> pendingBills = CompletableFuture.supplyAsync(() -> {
            try {
                var r = settlementClient.countByMerchantAndStatus(merchantId, 0);
                return r != null && r.getData() != null ? r.getData() : 0L;
            } catch (Exception e) { return 0L; }
        });

        CompletableFuture<Long> paidOrders = CompletableFuture.supplyAsync(() -> {
            try {
                var r = orderClient.countByMerchantAndStatus(merchantId, "PAID");
                return r != null && r.getData() != null ? r.getData() : 0L;
            } catch (Exception e) { return 0L; }
        });

        try {
            return Result.success(new DashboardSummaryVO(
                    pendingProducts.get(3, TimeUnit.SECONDS),
                    pendingBills.get(3, TimeUnit.SECONDS),
                    paidOrders.get(3, TimeUnit.SECONDS)));
        } catch (Exception e) {
            return Result.success(new DashboardSummaryVO(0L, 0L, 0L));
        }
    }
}
