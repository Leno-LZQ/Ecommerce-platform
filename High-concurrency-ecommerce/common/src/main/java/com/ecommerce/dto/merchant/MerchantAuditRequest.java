package com.ecommerce.dto.merchant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商家审核请求（供 admin BFF / Feign 内部调用）。
 */
@Data
public class MerchantAuditRequest {

    /** true=通过, false=拒绝 */
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    /** 审核备注 */
    @Size(max = 500)
    private String remark;
}
