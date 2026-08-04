package com.ecommerce.client;

import com.ecommerce.dto.user.UserProfileResponse;
import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/internal/users/batch")
    Result<Map<Long,UserProfileResponse>> getUsersByIds(@RequestParam List<Long> ids);

    /** 今日新增用户数（admin 看板） */
    @GetMapping("/internal/users/today-new")
    Result<Long> getTodayNewUsers();

    /** 内部：按角色名给用户分配角色（merchant-service 审核通过后调用） */
    @PostMapping("/internal/users/{userId}/roles")
    Result<Void> assignRole(@PathVariable Long userId, @RequestBody Map<String, String> body);

}
