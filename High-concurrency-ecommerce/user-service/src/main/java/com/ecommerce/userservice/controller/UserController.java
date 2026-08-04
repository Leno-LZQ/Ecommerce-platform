package com.ecommerce.userservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.result.Result;
import com.ecommerce.userservice.dto.AddressRequest;
import com.ecommerce.dto.user.UserProfileResponse;
import com.ecommerce.userservice.dto.UserUpdateRequest;
import com.ecommerce.userservice.entity.UserAddress;
import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.userservice.service.AddressService;
import com.ecommerce.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AddressService addressService;

    @GetMapping("/me")
    public Result<UserProfileResponse> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(userService.getUserProfile(userId));
    }

    @PutMapping("/me")
    public Result<UserProfileResponse> updateProfile(
        @Valid @RequestBody UserUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(userService.updateProfile(userId, request));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('user:update')")
    public Result<Void> updateStatus(
        @PathVariable Long userId,
        @RequestParam Integer status) {
        userService.updateStatus(userId, status);
        return Result.success();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:list')")
    public Result<IPage<UserProfileResponse>> getUserList(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.listProfiles(new Page<>(page, size)));
    }


    @GetMapping("/{userId}")                   // 管理员查任意用户详情
    @PreAuthorize("hasAuthority('user:list')")
    public Result<UserProfileResponse> getUserById(@PathVariable Long userId){
        return Result.success(userService.getUserProfile(userId));
    }

    // 注销账号（逻辑删除 + 清敏感数据）
    @DeleteMapping("/me")
    public Result<Void> deleteMyAccount(){
        Long userId = SecurityUtils.getCurrentUserId();
        userService.delete(userId);
        return Result.success();
    }


// ===== 地址管理 =====

    @GetMapping("/addresses")
    public Result<List<UserAddress>> listAddresses() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(addressService.listByUserId(userId));
    }

    @PostMapping("/addresses")
    public Result<UserAddress> createAddress(@Valid @RequestBody AddressRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(addressService.create(userId, request));
    }

    @PutMapping("/addresses/{id}")
    public Result<Void> updateAddress(@PathVariable Long id,
                                       @Valid @RequestBody AddressRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        addressService.update(userId, id, request);
        return Result.success();
    }

    @DeleteMapping("/addresses/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        addressService.delete(userId, id);
        return Result.success();
    }

    @PutMapping("/addresses/{id}/default")
    public Result<Void> setDefaultAddress(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        addressService.setDefault(userId, id);
        return Result.success();
    }


// ===== 搜索类（管理后台 / 商家端用）=====

    @GetMapping("/search")                    // 关键字 + 分页搜索
    @PreAuthorize("hasAnyAuthority('user:list','order:view')")
    public Result<IPage<UserProfileResponse>> searchUsers(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size){
        return Result.success(userService.searchProfiles(keyword, new Page<>(page, size)));
    }
}
