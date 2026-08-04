package com.ecommerce.merchantservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SubAccountAddRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "运营|客服|财务", message = "角色必须为：运营/客服/财务")
    private String role;
}
