package com.ecommerce.userservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ecommerce.userservice.entity.Permission;
import com.ecommerce.userservice.entity.RolePermission;
import com.ecommerce.userservice.mapper.PermissionMapper;
import com.ecommerce.userservice.mapper.RolePermissionMapper;
import com.ecommerce.userservice.service.PermissionService;
import cn.hutool.core.util.IdUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public List<Permission> listAll() {
        return permissionMapper.selectList(null);
    }

    @Override
    public List<Permission> listByRole(Long roleId) {
        List<RolePermission> rps = rolePermissionMapper.selectList(
            Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, roleId));
        if (rps.isEmpty()) {
            return List.of();
        }
        List<Long> permIds = rps.stream().map(RolePermission::getPermissionId).toList();
        return permissionMapper.selectBatchIds(permIds);
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        // 清空旧关联
        rolePermissionMapper.delete(
            Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, roleId));

        // 批量插入新关联
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<RolePermission> list = new ArrayList<>();
            for (Long permId : permissionIds) {
                RolePermission rp = new RolePermission();
                rp.setId(IdUtil.getSnowflakeNextId());
                rp.setRoleId(roleId);
                rp.setPermissionId(permId);
                list.add(rp);
            }
            for (RolePermission rp : list) {
                rolePermissionMapper.insert(rp);
            }
        }
        log.info("角色 {} 权限已更新，共 {} 项", roleId,
            permissionIds == null ? 0 : permissionIds.size());
    }
}
