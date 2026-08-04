package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AssignPermissionRequest {
    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    private List<Long> permissionIds;
}
