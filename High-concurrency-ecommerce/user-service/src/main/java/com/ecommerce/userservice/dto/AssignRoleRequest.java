package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignRoleRequest {
    @NotNull(message = "角色ID不能为空")
    private Long roleId;
}
