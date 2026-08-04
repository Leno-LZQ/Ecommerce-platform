package com.ecommerce.csservice.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * WebSocket 会话反向映射信息
 */
@Data
@AllArgsConstructor
public class SessionInfo {

    /**
     * 角色：USER / AGENT
     */
    private String role;

    /**
     * 对应业务 ID：userId 或 agentId
     */
    private Long id;
}
