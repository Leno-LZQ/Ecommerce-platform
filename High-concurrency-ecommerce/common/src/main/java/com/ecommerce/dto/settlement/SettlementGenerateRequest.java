package com.ecommerce.dto.settlement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 生成结算账单请求。
 */
@Data
public class SettlementGenerateRequest {

    @NotNull
    private Long merchantId;

    @NotNull
    private LocalDate periodStart;

    @NotNull
    private LocalDate periodEnd;

    /** 待结算订单快照列表 */
    private List<OrderSnapshot> orders;

    @Data
    public static class OrderSnapshot {

        @NotBlank
        private String subOrderNo;

        @NotNull
        private BigDecimal orderAmount;

        private BigDecimal discountAmount;

        private Long categoryId;
    }
}
