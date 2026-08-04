package com.ecommerce.userservice.service;



import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.user.UserProfileResponse;
import com.ecommerce.userservice.dto.UserUpdateRequest;
import com.ecommerce.userservice.entity.User;

import java.util.List;
import java.util.Map;

public interface UserService {

    /** 按 ID 查询（找不到抛 BusinessException） */
    User getById(Long id);

    /** 按手机号哈希查询 */
    User getByPhone(String phoneHash);

    /** 获取当前登录用户的个人资料（用于 /api/users/me） */
    UserProfileResponse getUserProfile(Long userId);

    /** 批量获取用户的个人资料 */
    Map<Long, UserProfileResponse> getUserProfiles(List<Long> ids);

    /** 更新昵称/头像等（敏感字段不允许通过此接口修改） */
    UserProfileResponse updateProfile(Long userId, UserUpdateRequest request);

    /** 更新账号状态（仅管理员调用） */
    void updateStatus(Long userId, Integer status);

    void delete(Long userId);

    /** 获取所有用户（仅管理员调用） */
    IPage<UserProfileResponse> listProfiles(Page<User> page);

    /** 搜索用户（仅管理员调用） */
    IPage<UserProfileResponse> searchProfiles(String keyword,Page<User> page);

    /** 今日新增用户数（admin 看板） */
    long countTodayNew();
}
