package com.ecommerce.merchantservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuditRequest {

    /** true=通过, false=拒绝 */
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    /** 审核备注（通过时可选，拒绝时必填） */
    @Size(max = 500)
    private String remark;
}
