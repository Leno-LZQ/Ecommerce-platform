package com.ecommerce.merchantservice.controller;

import com.ecommerce.dto.merchant.MerchantAuditRequest;
import com.ecommerce.dto.merchant.MerchantSummaryDTO;
import com.ecommerce.merchantservice.dto.AuditRequest;
import com.ecommerce.merchantservice.dto.MerchantVO;
import com.ecommerce.merchantservice.service.MerchantService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class MerchantInternalController {

    private final MerchantService merchantService;

    /** 商家总数（admin 看板） */
    @GetMapping("/merchants/count")
    public Result<Long> getCount() {
        return Result.success(merchantService.countAll());
    }

    /** 待审核商家数（admin 看板） */
    @GetMapping("/merchants/pending-count")
    public Result<Long> getPendingCount() {
        return Result.success(merchantService.countPending());
    }

    /** 内部审核（admin BFF 代理） */
    @PostMapping("/merchants/{id}/audit")
    public Result<MerchantSummaryDTO> audit(@PathVariable Long id,
                                             @RequestParam Long operatorId,
                                             @Valid @RequestBody MerchantAuditRequest req) {
        // 转换为 merchant-service 本地 DTO
        AuditRequest localReq = new AuditRequest();
        localReq.setApproved(req.getApproved());
        localReq.setRemark(req.getRemark());
        return Result.success(toSummaryDTO(merchantService.audit(id, localReq, operatorId)));
    }

    /** 内部冻结（admin BFF 代理） */
    @PutMapping("/merchants/{id}/freeze")
    public Result<MerchantSummaryDTO> freeze(@PathVariable Long id,
                                              @RequestParam Long operatorId,
                                              @RequestParam(defaultValue = "") String reason) {
        return Result.success(toSummaryDTO(merchantService.freeze(id, reason, operatorId)));
    }

    /** 商家是否存在 */
    @GetMapping("/merchants/{id}/exists")
    public Result<Boolean> exists(@PathVariable Long id) {
        return Result.success(merchantService.exists(id));
    }

    /** 按userId查商家信息 */
    @GetMapping("/merchants/by-user/{userId}")
    public Result<MerchantSummaryDTO> getByUserId(@PathVariable Long userId) {
        return Result.success(toSummaryDTO(merchantService.getByUserId(userId)));
    }

    /** 按userId查商家ID（精简版，供AuthService用） */
    @GetMapping("/merchants/by-user/{userId}/id")
    public Result<Long> getMerchantIdByUserId(@PathVariable Long userId) {
        return Result.success(merchantService.getByUserId(userId).getId());
    }

    /** 获取所有商家 ID 列表（供批量查询等内部场景） */
    @GetMapping("/merchants/ids")
    public Result<java.util.List<Long>> listAllIds() {
        return Result.success(merchantService.listAllIds());
    }

    // ==================== VO → 公共 DTO 转换 ====================

    private static MerchantSummaryDTO toSummaryDTO(MerchantVO vo) {
        return MerchantSummaryDTO.builder()
                .id(vo.getId())
                .userId(vo.getUserId())
                .shopName(vo.getShopName())
                .shopLogo(vo.getShopLogo())
                .contactName(vo.getContactName())
                .contactPhone(vo.getContactPhone())
                .status(vo.getStatus())
                .auditRemark(vo.getAuditRemark())
                .bankName(vo.getBankName())
                .bankAccountMasked(vo.getBankAccountMasked())
                .accountHolder(vo.getAccountHolder())
                .createTime(vo.getCreateTime())
                .updateTime(vo.getUpdateTime())
                .build();
    }
}
