package com.ecommerce.csservice.websocket;

import com.ecommerce.csservice.entity.CsAgent;
import com.ecommerce.csservice.service.AgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 客服 WebSocket 握手拦截器
 *
 * <p>从网关注入的请求头中读取用户身份，决定是用户连接还是客服连接。</p>
 */
@Slf4j
@Component
public class CsHandshakeInterceptor implements HandshakeInterceptor {

    private final AgentService agentService;

    public CsHandshakeInterceptor(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {
        HttpHeaders headers = request.getHeaders();

        String userIdStr = headers.getFirst("X-User-Id");
        if (userIdStr == null || userIdStr.isEmpty()) {
            log.warn("客服 WS 握手被拒绝：缺少 X-User-Id");
            return false;
        }

        Long userId;
        try {
            userId = Long.valueOf(userIdStr);
        } catch (NumberFormatException e) {
            log.warn("客服 WS 握手被拒绝：非法 userId {}", userIdStr);
            return false;
        }

        String roles = headers.getFirst("X-Roles");
        if (roles == null || roles.isEmpty()) {
            log.warn("客服 WS 握手被拒绝：缺少 X-Roles");
            return false;
        }

        // 判断是否是客服角色
        boolean isAgent = roles.contains("ROLE_CS_AGENT") || roles.contains("ROLE_CS_MANAGER");
        Long agentId = null;
        if (isAgent) {
            CsAgent agent = agentService.getByUserId(userId);
            if (agent == null) {
                log.warn("客服 WS 握手被拒绝：用户 {} 无客服身份", userId);
                return false;
            }
            agentId = agent.getId();
            attributes.put("agentId", agentId);
            attributes.put("agentUserId", userId);
        }

        attributes.put("userId", userId);
        attributes.put("role", isAgent ? "AGENT" : "USER");

        log.info("客服 WS 握手成功：userId={}, role={}, agentId={}", userId, isAgent ? "AGENT" : "USER", agentId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception != null) {
            log.error("客服 WS 握手异常", exception);
        }
    }
}
