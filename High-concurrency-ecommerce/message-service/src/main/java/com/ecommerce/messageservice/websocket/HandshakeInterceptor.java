package com.ecommerce.messageservice.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Map;

@Slf4j
@Component
public class HandshakeInterceptor implements org.springframework.web.socket.server.HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        HttpHeaders headers = request.getHeaders();

        // 1. 获取并校验用户ID
        String userIdStr = headers.getFirst("X-User-Id");
        if (userIdStr == null || userIdStr.isEmpty()) {
            log.warn("WebSocket 握手被拒绝：缺少 X-User-Id，请求可能绕过了网关");
            return false;
        }

        // 2. 获取角色（注意：这里是 roles，但只取第一个角色用）
        String roles = headers.getFirst("X-Roles");
        if (roles == null || roles.isEmpty()) {
            log.warn("WebSocket 握手被拒绝：缺少 X-Roles");
            return false;
        }
        // 归一化为 Handler 识别的角色：商家优先（商家同时拥有 ROLE_USER + ROLE_MERCHANT）
        String role;
        if (roles.contains("ROLE_MERCHANT")) {
            role = "MERCHANT";
        } else if (roles.contains("ROLE_USER")) {
            role = "USER";
        } else {
            role = roles.split(",")[0].trim();
        }

        // 3. 获取商家ID（可选）
        String merchantIdStr = headers.getFirst("X-Merchant-Id");
        Long merchantId = null;
        if (merchantIdStr != null && !merchantIdStr.isEmpty()) {
            merchantId = Long.valueOf(merchantIdStr);
        }

        // 4. 解析 userId（加异常处理）
        Long userId;
        try {
            userId = Long.valueOf(userIdStr);
        } catch (NumberFormatException e) {
            log.error("WebSocket 握手被拒绝：非法的 userId: {}", userIdStr);
            return false;
        }

        // 5. 存入 attributes（注意 Key 要和 Handler 里一致！）
        attributes.put("userId", userId);
        attributes.put("role", role);           // ✅ 用 "role" 不是 "roles"
        attributes.put("merchantId", merchantId);

        log.info("WebSocket 握手成功：userId={}, role={}, merchantId={}",
            userId, role, merchantId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception != null) {
            log.error("WebSocket 握手异常", exception);
        } else {
            log.debug("WebSocket 握手完成");
        }
    }
}
