package com.ecommerce.merchantservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.merchantservice.dto.AuditRequest;
import com.ecommerce.merchantservice.dto.MerchantVO;
import com.ecommerce.merchantservice.service.MerchantService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/merchants")
@RequiredArgsConstructor
public class MerchantAuditController {

    private final MerchantService merchantService;

    /** 商家列表 */
    @GetMapping
    public Result<List<MerchantVO>> list(@RequestParam(required = false) Integer status,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size) {
        return Result.success(merchantService.pageByStatus(status, page, size));
    }

    /** 商家详情 */
    @GetMapping("/{id}")
    public Result<MerchantVO> detail(@PathVariable Long id) {
        MerchantVO vo = merchantService.getById(id);
        vo.setAuditLogs(merchantService.getAuditLogs(id));
        return Result.success(vo);
    }

    /** 审核（通过/拒绝） */
    @PostMapping("/{id}/audit")
    public Result<MerchantVO> audit(@PathVariable Long id,
                                     @Valid @RequestBody AuditRequest req) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.success(merchantService.audit(id, req, operatorId));
    }

    /** 审核日志 */
    @GetMapping("/{id}/audit-logs")
    public Result<List<MerchantVO.AuditLogVO>> auditLogs(@PathVariable Long id) {
        return Result.success(merchantService.getAuditLogs(id));
    }

    /** 冻结商家 */
    @PutMapping("/{id}/freeze")
    public Result<MerchantVO> freeze(@PathVariable Long id,
                                      @RequestParam(defaultValue = "") String reason) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.success(merchantService.freeze(id, reason, operatorId));
    }

    /** 解冻商家 */
    @PutMapping("/{id}/unfreeze")
    public Result<MerchantVO> unfreeze(@PathVariable Long id) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.success(merchantService.unfreeze(id, operatorId));
    }

    /** 注销商家 */
    @PutMapping("/{id}/close")
    public Result<MerchantVO> close(@PathVariable Long id) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        return Result.success(merchantService.close(id, operatorId));
    }

    /** 逐条审核资质 */
    @PutMapping("/{merchantId}/qualifications/{qualId}/audit")
    public Result<Void> auditQualification(@PathVariable Long merchantId,
                                            @PathVariable Long qualId,
                                            @Valid @RequestBody AuditRequest req) {
        merchantService.auditQualification(qualId,
                Boolean.TRUE.equals(req.getApproved()), req.getRemark());
        return Result.success();
    }
}
