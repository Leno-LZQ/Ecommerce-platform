package com.ecommerce.userservice.controller;

import com.ecommerce.result.Result;
import com.ecommerce.userservice.dto.AssignPermissionRequest;
import com.ecommerce.userservice.dto.AssignRoleRequest;
import com.ecommerce.userservice.dto.CreateRoleRequest;
import com.ecommerce.userservice.dto.UpdateRoleRequest;
import com.ecommerce.userservice.entity.Permission;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.service.PermissionService;
import com.ecommerce.userservice.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    // ==================== 角色管理 ====================

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    public Result<List<Role>> listRoles() {
        return Result.success(roleService.listAll());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    public Result<Role> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return Result.success(roleService.create(request.getName(), request.getDescription()));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    public Result<Role> updateRole(@PathVariable Long id,
                                    @Valid @RequestBody UpdateRoleRequest request) {
        return Result.success(roleService.update(id, request.getName(), request.getDescription()));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }

    // ==================== 角色权限管理 ====================

    @GetMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('role:manage')")
    public Result<List<Permission>> getRolePermissions(@PathVariable Long id) {
        return Result.success(permissionService.listByRole(id));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('role:manage')")
    public Result<Void> setRolePermissions(@PathVariable Long id,
                                            @Valid @RequestBody AssignPermissionRequest request) {
        permissionService.assignPermissions(id, request.getPermissionIds());
        return Result.success();
    }

    // ==================== 权限项查询 ====================

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('role:manage')")
    public Result<List<Permission>> listPermissions() {
        return Result.success(permissionService.listAll());
    }

    // ==================== 用户角色分配 ====================

    @GetMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    public Result<List<Role>> getUserRoles(@PathVariable Long id) {
        return Result.success(roleService.getUserRoles(id));
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    public Result<Void> assignUserRole(@PathVariable Long id,
                                        @Valid @RequestBody AssignRoleRequest request) {
        roleService.assignRole(id, request.getRoleId());
        return Result.success();
    }

    @DeleteMapping("/users/{id}/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:manage')")
    public Result<Void> removeUserRole(@PathVariable Long id,
                                        @PathVariable Long roleId) {
        roleService.removeRole(id, roleId);
        return Result.success();
    }
}
