package com.ecommerce.userservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.UserRole;
import com.ecommerce.userservice.mapper.RoleMapper;
import com.ecommerce.userservice.mapper.UserRoleMapper;
import com.ecommerce.userservice.service.RoleService;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    private static final long PRESET_ROLE_MAX_ID = 5;

    @Override
    public List<Role> listAll() {
        return roleMapper.selectList(null);
    }

    @Override
    public Role create(String name, String description) {
        Long count = roleMapper.selectCount(
            Wrappers.<Role>lambdaQuery().eq(Role::getName, name));
        if (count > 0) {
            throw new BusinessException(ErrorCode.ROLE_NAME_DUPLICATE);
        }
        Role role = new Role();
        role.setId(IdUtil.getSnowflakeNextId());
        role.setName(name);
        role.setDescription(description);
        roleMapper.insert(role);
        log.info("创建角色 id={}, name={}", role.getId(), name);
        return role;
    }

    @Override
    public Role update(Long roleId, String name, String description) {
        Role role = getRoleById(roleId);
        if (name != null) {
            // 检查新名称是否与其他角色冲突
            Long count = roleMapper.selectCount(
                Wrappers.<Role>lambdaQuery()
                    .eq(Role::getName, name)
                    .ne(Role::getId, roleId));
            if (count > 0) {
                throw new BusinessException(ErrorCode.ROLE_NAME_DUPLICATE);
            }
            role.setName(name);
        }
        if (description != null) {
            role.setDescription(description);
        }
        roleMapper.updateById(role);
        log.info("更新角色 id={}", roleId);
        return role;
    }

    @Override
    public void delete(Long roleId) {
        if (roleId <= PRESET_ROLE_MAX_ID) {
            throw new BusinessException(ErrorCode.ROLE_PRESET_CANNOT_DELETE);
        }
        Role role = getRoleById(roleId);
        roleMapper.deleteById(role.getId());
        log.info("删除角色 id={}", roleId);
    }

    @Override
    public List<Role> getUserRoles(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
            Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        return roleMapper.selectBatchIds(roleIds);
    }

    @Override
    @Transactional
    public void assignRole(Long userId, Long roleId) {
        getRoleById(roleId);   // 校验角色存在

        Long count = userRoleMapper.selectCount(
            Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, roleId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.ROLE_ALREADY_ASSIGNED);
        }

        Long id = IdUtil.getSnowflakeNextId();
        userRoleMapper.insertUserRole(id, userId, roleId);
        log.info("用户 {} 分配角色 {}", userId, roleId);
    }

    @Override
    @Transactional
    public void removeRole(Long userId, Long roleId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
            Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
        if (userRoles.size() <= 1) {
            throw new BusinessException(ErrorCode.USER_MUST_HAVE_ROLE);
        }

        UserRole target = userRoles.stream()
            .filter(ur -> ur.getRoleId().equals(roleId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

        userRoleMapper.deleteById(target.getId());
        log.info("用户 {} 移除角色 {}", userId, roleId);
    }

    @Override
    @Transactional
    public void assignRoleByName(Long userId, String roleName) {
        Role role = roleMapper.selectOne(
            Wrappers.<Role>lambdaQuery().eq(Role::getName, roleName));
        if (role == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        Long count = userRoleMapper.selectCount(
            Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId()));
        if (count > 0) {
            log.info("用户 {} 已拥有角色 {}, 跳过", userId, roleName);
            return;
        }
        Long id = IdUtil.getSnowflakeNextId();
        userRoleMapper.insertUserRole(id, userId, role.getId());
        log.info("用户 {} 分配角色 {}", userId, roleName);
    }
    private Role getRoleById(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }
}
