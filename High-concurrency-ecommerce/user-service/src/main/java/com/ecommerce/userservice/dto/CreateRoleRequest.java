package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "角色名不能为空")
    @Size(max = 30, message = "角色名最长30个字符")
    private String name;

    @Size(max = 100, message = "角色描述最长100个字符")
    private String description;
}
