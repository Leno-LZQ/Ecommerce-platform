package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuditRequest {
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;             // true=通过, false=拒绝

    @Size(max = 255, message = "审核备注最长255字符")
    private String remark;                // 拒绝时填写原因
}
