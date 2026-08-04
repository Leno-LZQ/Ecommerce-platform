package com.ecommerce.userservice.service;

import com.ecommerce.userservice.entity.Role;

import java.util.List;

public interface RoleService {

    /** 获取所有角色 */
    List<Role> listAll();

    /** 创建角色 */
    Role create(String name, String description);

    /** 修改角色 */
    Role update(Long roleId, String name, String description);

    /** 删除角色（预置角色 id≤5 不可删除） */
    void delete(Long roleId);

    /** 获取用户的所有角色 */
    List<Role> getUserRoles(Long userId);

    /** 给用户分配角色 */
    void assignRole(Long userId, Long roleId);

    /** 移除用户角色（至少保留一个） */
    void removeRole(Long userId, Long roleId);

    /** 按角色名给用户分配角色（幂等，已拥有则跳过） */
    void assignRoleByName(Long userId, String roleName);
}
