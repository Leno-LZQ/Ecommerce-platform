package com.ecommerce.settlementservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.settlement.SettlementBillDTO;
import com.ecommerce.dto.settlement.SettlementGenerateRequest;
import com.ecommerce.result.Result;
import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.settlementservice.entity.SettlementBill;
import com.ecommerce.settlementservice.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    // ══════════════════════════════════════════
    //  内部接口（SettlementClient）
    // ══════════════════════════════════════════

    /** 统计商家指定状态的账单数 */
    @GetMapping("/internal/settlement/bills/count")
    public Result<Long> countByMerchantAndStatus(@RequestParam Long merchantId,
                                                  @RequestParam Integer status) {
        return Result.success(settlementService.countByMerchantAndStatus(merchantId, status));
    }

    // ══════════════════════════════════════════
    //  管理端接口
    // ══════════════════════════════════════════

    /** 生成结算账单 */
    @PostMapping("/internal/settlement/bills/generate")
    public Result<SettlementBillDTO> generate(@Valid @RequestBody SettlementGenerateRequest request) {
        var orders = request.getOrders() != null ? request.getOrders().stream()
                .map(o -> new SettlementService.OrderSnapshot(
                        o.getSubOrderNo(),
                        o.getOrderAmount(),
                        o.getDiscountAmount() != null ? o.getDiscountAmount() : java.math.BigDecimal.ZERO,
                        o.getCategoryId()))
                .toList() : java.util.List.<SettlementService.OrderSnapshot>of();

        return Result.success(toDTO(settlementService.generateBill(
                request.getMerchantId(), request.getPeriodStart(), request.getPeriodEnd(), orders)));
    }

    /** 确认账单 */
    @PutMapping("/internal/settlement/bills/{billNo}/confirm")
    public Result<Void> confirm(@PathVariable String billNo) {
        settlementService.confirmBill(billNo);
        return Result.success();
    }

    /** 结算打款 */
    @PutMapping("/internal/settlement/bills/{billNo}/settle")
    public Result<Void> settle(@PathVariable String billNo) {
        settlementService.settleBill(billNo);
        return Result.success();
    }

    // ══════════════════════════════════════════
    //  商家端接口
    // ══════════════════════════════════════════

    /** 商家查自己的账单列表 */
    @GetMapping("/api/settlement/bills")
    public Result<Page<SettlementBill>> listBills(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long merchantId = SecurityUtils.getCurrentMerchantId();
        return Result.success(settlementService.listBills(merchantId, status, page, size));
    }

    // ==================== 实体 → 公共 DTO 转换 ====================

    private static SettlementBillDTO toDTO(SettlementBill entity) {
        return SettlementBillDTO.builder()
                .id(entity.getId())
                .billNo(entity.getBillNo())
                .merchantId(entity.getMerchantId())
                .periodStart(entity.getPeriodStart())
                .periodEnd(entity.getPeriodEnd())
                .totalOrderAmount(entity.getTotalOrderAmount())
                .totalDiscount(entity.getTotalDiscount())
                .totalCommission(entity.getTotalCommission())
                .settlementAmount(entity.getSettlementAmount())
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .build();
    }
}
