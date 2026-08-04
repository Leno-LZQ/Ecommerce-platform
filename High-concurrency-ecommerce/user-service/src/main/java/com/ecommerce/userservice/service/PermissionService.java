package com.ecommerce.userservice.service;

import com.ecommerce.userservice.entity.Permission;

import java.util.List;

public interface PermissionService {

    /** 获取所有权限项 */
    List<Permission> listAll();

    /** 获取角色的权限列表 */
    List<Permission> listByRole(Long roleId);

    /** 设置角色权限（先清空旧关联，再批量插入新关联） */
    void assignPermissions(Long roleId, List<Long> permissionIds);
}
