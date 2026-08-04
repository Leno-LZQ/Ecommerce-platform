package com.ecommerce.userservice.controller;

import com.ecommerce.dto.user.UserProfileResponse;
import com.ecommerce.result.Result;
import com.ecommerce.userservice.service.RoleService;
import com.ecommerce.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    @Autowired private UserService userService;
    private final RoleService roleService;

    @GetMapping("/users/batch")
    public Result<Map<Long, UserProfileResponse>> getUsersByIds(
        @RequestParam List<Long> ids) {
        return Result.success(userService.getUserProfiles(ids));
    }

    /** 今日新增用户数（admin 看板） */
    @GetMapping("/users/today-new")
    public Result<Long> getTodayNewUsers() {
        return Result.success(userService.countTodayNew());
    }

    /** 内部：按角色名给用户分配角色（merchant-service 审核通过后调用） */
    @PostMapping("/users/{userId}/roles")
    public Result<Void> assignRole(@PathVariable Long userId,
                                   @RequestBody Map<String, String> body) {
        roleService.assignRoleByName(userId, body.get("roleName"));
        return Result.success();
    }

}
