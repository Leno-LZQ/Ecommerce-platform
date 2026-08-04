package com.ecommerce.userservice.service.impl;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.dto.user.UserProfileResponse;
import com.ecommerce.userservice.dto.UserUpdateRequest;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.mapper.UserMapper;
import com.ecommerce.userservice.service.UserService;
import com.ecommerce.userservice.util.PhoneCryptoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;


    @Value("${security.phone.aes-key}")
    private String aesKey;

    @Override
    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public User getByPhone(String phoneHash) {
        return userMapper.selectByPhoneHash(phoneHash);
    }

    @Override
    public UserProfileResponse getUserProfile(Long userId) {
        User user = getById(userId);
        return buildProfile(user);
    }

    @Override
    public Map<Long,UserProfileResponse> getUserProfiles(List<Long> ids){
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectBatchIds(ids);
        return users.stream()
            .collect(Collectors.toMap(User::getId, this::buildProfile));
    }

    @Override
    public UserProfileResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = getById(userId);

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }

        userMapper.updateById(user);
        log.info("用户 {} 更新个人资料", userId);
        return buildProfile(user);
    }

    @Override
    public void updateStatus(Long userId, Integer status) {
        User user = getById(userId);
        user.setStatus(status);
        userMapper.updateById(user);
        log.info("管理员变更用户 {} 状态为 {}", userId, status);
    }

    @Override
    public IPage<UserProfileResponse> listProfiles(Page<User> page) {
        Page<User> userPage = userMapper.selectPage(
            page, Wrappers.<User>lambdaQuery().eq(User::getDeleted, 0));
        return userPage.convert(this::buildProfile);
    }

    @Override
    public void delete(Long userId) {
        User user = getById(userId);
        user.setDeleted(1);
        user.setPhoneHash(null);
        user.setPhoneEncrypted(null);
        userMapper.updateById(user);
        log.info("用户 {} 已注销", userId);
    }


    @Override
    public IPage<UserProfileResponse> searchProfiles(String keyword,Page<User> page){
        Page<User> userPage = userMapper.selectPage(
            page, Wrappers.<User>lambdaQuery().eq(User::getDeleted, 0).like(User::getUsername, keyword));
        return userPage.convert(this::buildProfile);
    }

    @Override
    public long countTodayNew() {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        return userMapper.selectCount(
                Wrappers.<User>lambdaQuery()
                    .eq(User::getDeleted, 0)
                    .ge(User::getCreateTime, todayStart));
    }
    // ========== 辅助方法 ==========

    private UserProfileResponse buildProfile(User user) {
        String maskedPhone = "***";
        if (user.getPhoneEncrypted() != null) {
            try {
                String phone = PhoneCryptoUtils.decrypt(
                    user.getPhoneEncrypted(), aesKey);
                maskedPhone = PhoneCryptoUtils.mask(phone);
            } catch (Exception e) {
                log.warn("手机号脱敏失败 userId={}", user.getId(), e);
            }
        }
        return UserProfileResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .avatar(user.getAvatar())
            .maskedPhone(maskedPhone)
            .email(user.getEmail())
            .status(user.getStatus())
            .build();
    }
}
