package com.ecommerce.dto.settlement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 结算账单 DTO（供 Feign 内部返回）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementBillDTO {

    private Long id;
    private String billNo;
    private Long merchantId;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    /** 周期内订单总额 */
    private BigDecimal totalOrderAmount;

    /** 周期内优惠总额 */
    private BigDecimal totalDiscount;

    /** 应缴佣金总额 */
    private BigDecimal totalCommission;

    /** 实际结算金额 */
    private BigDecimal settlementAmount;

    /** 状态：0=草稿 1=已确认 2=已打款 */
    private Integer status;

    private LocalDateTime createTime;
}
