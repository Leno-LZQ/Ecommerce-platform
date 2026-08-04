package com.ecommerce.client;

import com.ecommerce.dto.merchant.MerchantAuditRequest;
import com.ecommerce.dto.merchant.MerchantSummaryDTO;
import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "merchant-service")
public interface MerchantClient {

    // ══════════════════════════════════════════
    //  对接 MerchantInternalController (/internal)
    // ══════════════════════════════════════════

    /** 商家总数（admin 看板） */
    @GetMapping("/internal/merchants/count")
    Result<Long> getTotalCount();

    /** 待审核商家数（admin 看板） */
    @GetMapping("/internal/merchants/pending-count")
    Result<Long> getPendingAuditCount();

    /** 内部审核（admin BFF 代理） */
    @PostMapping("/internal/merchants/{id}/audit")
    Result<MerchantSummaryDTO> audit(@PathVariable Long id,
                                      @RequestParam Long operatorId,
                                      @RequestBody MerchantAuditRequest req);

    /** 内部冻结（admin BFF 代理） */
    @PutMapping("/internal/merchants/{id}/freeze")
    Result<MerchantSummaryDTO> freeze(@PathVariable Long id,
                                       @RequestParam Long operatorId,
                                       @RequestParam(defaultValue = "") String reason);

    /** 商家是否存在 */
    @GetMapping("/internal/merchants/{id}/exists")
    Result<Boolean> exists(@PathVariable Long id);

    /** 按 userId 查商家信息 */
    @GetMapping("/internal/merchants/by-user/{userId}")
    Result<MerchantSummaryDTO> getByUserId(@PathVariable Long userId);

    /** 按 userId 查商家 ID（精简版，供 AuthService 用） */
    @GetMapping("/internal/merchants/by-user/{userId}/id")
    Result<Long> getMerchantIdByUserId(@PathVariable Long userId);

    /** 获取所有商家 ID 列表（供批量查询等内部场景） */
    @GetMapping("/internal/merchants/ids")
    Result<List<Long>> listAllIds();

}
