package com.ecommerce.client;

import com.ecommerce.dto.settlement.SettlementBillDTO;
import com.ecommerce.dto.settlement.SettlementGenerateRequest;
import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "settlement-service")
public interface SettlementClient {

    // ══════════════════════════════════════════
    //  对接 SettlementController
    // ══════════════════════════════════════════

    /** 统计商家指定状态的账单数 */
    @GetMapping("/internal/settlement/bills/count")
    Result<Long> countByMerchantAndStatus(@RequestParam Long merchantId,
                                           @RequestParam Integer status);

    /** 生成结算账单（定时任务/管理端触发） */
    @PostMapping("/internal/settlement/bills/generate")
    Result<SettlementBillDTO> generate(@RequestBody SettlementGenerateRequest request);

    /** 确认账单 */
    @PutMapping("/internal/settlement/bills/{billNo}/confirm")
    Result<Void> confirm(@PathVariable String billNo);

    /** 结算打款 */
    @PutMapping("/internal/settlement/bills/{billNo}/settle")
    Result<Void> settle(@PathVariable String billNo);
}
