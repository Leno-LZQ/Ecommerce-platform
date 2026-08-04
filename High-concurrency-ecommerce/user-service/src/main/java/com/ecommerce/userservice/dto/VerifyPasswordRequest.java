package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyPasswordRequest {
    @NotBlank(message = "密码不能为空")
    private String password;
}
