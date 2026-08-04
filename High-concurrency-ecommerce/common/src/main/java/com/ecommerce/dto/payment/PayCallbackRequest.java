package com.ecommerce.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模拟支付回调请求（测试用）。
 */
@Data
public class PayCallbackRequest {

    /** 支付单号 */
    @NotBlank(message = "支付单号不能为空")
    private String payNo;

    /** 是否支付成功 */
    @NotBlank(message = "支付结果不能为空")
    private String success;
}
