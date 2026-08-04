package com.ecommerce.userservice.controller;


import com.ecommerce.client.MerchantClient;
import com.ecommerce.result.Result;
import com.ecommerce.userservice.dto.*;
import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.userservice.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    /** 发送验证码 */
    @PostMapping("/send-code")
    public Result<Map<String, Object>> sendCode(@Valid @RequestBody SendCodeRequest req) {
        authService.sendSmsCode(req.getPhone());
        return Result.success(Map.of("sent", true, "expireSeconds", 300));
    }

    /** 验证码登录（自动注册） */
    @PostMapping("/login-by-code")
    public Result<LoginResponse> loginByCode(@Valid @RequestBody LoginByCodeRequest req) {
        return Result.success(authService.loginByCode(req.getPhone(), req.getCode()));
    }

    /** 密码登录 */
    @PostMapping("/login-by-password")
    public Result<LoginResponse> loginByPassword(@Valid @RequestBody LoginByPasswordRequest req) {
        return Result.success(authService.loginByPassword(req.getPhone(), req.getPassword()));
    }

    /** 刷新 Token */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        return Result.success(authService.extendSession(body.get("sessionId")));
    }

    /** 登出 */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody Map<String, String> body) {
        authService.logout(body.get("sessionId"));
        return Result.success();
    }

    /** 修改密码（需已登录） */
    @PutMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        authService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
        return Result.success();
    }

    /** 重置密码（需已登录） */
    @PutMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.getPhone(), req.getCode(), req.getNewPassword());
        return Result.success();
    }

    /** 验证密码（需已登录） */
    @PostMapping("/verify-password")
    public Result<Map<String,Object>> verifyPassword(@Valid @RequestBody VerifyPasswordRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        String password = req.getPassword();
        return Result.success(authService.verifyPassword(userId,password));
    }

    /** 修改手机号（需已登录） */
    @PutMapping("/change-phone")
    public Result<Void> changePhone(@Valid @RequestBody ChangePhoneRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        String permitToken = req.getPermitToken();
        String phone = req.getPhone();
        String newPhoneCode = req.getCode();
        authService.confirmPhone(userId,permitToken,phone,newPhoneCode);
        return Result.success();
    }
}
