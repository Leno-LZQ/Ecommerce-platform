package com.ecommerce.dto.promotion;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款请求（order-service → /internal/promotion/refund）
 */
@Data
public class RefundRequest {

    /** 订单号 */
    @NotNull
    private String orderNo;

    /**
     * 退款比例（0 < ratio <= 1）
     * 1.0 = 全额退款 → 券 status→4（已退还，不可再用）；
     * < 1.0 = 部分退款 → 券保持已用，仅预算按比例回补。
     */
    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("1.00")
    private BigDecimal refundRatio;
}
