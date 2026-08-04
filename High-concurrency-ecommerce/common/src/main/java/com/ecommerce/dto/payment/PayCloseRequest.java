package com.ecommerce.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 关闭支付单请求（Saga 补偿调用���。
 */
@Data
public class PayCloseRequest {

    /** 支付单号 */
    @NotBlank(message = "支付单号不能为空")
    private String payNo;
}
