package com.ecommerce.gateway.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "gateway")
@Component
public class AppGatewayProperties {
    /**
     * 无需鉴权的路径列表（支持 Ant 风格通配符）
     */
    private List<String> whitelist = new ArrayList<>();

    /**
     * WebSocket 配置
     */
    private WsConfig ws = new WsConfig();

    @Data
    public static class WsConfig {
        private int maxConnections = 5000;
        private int idleTimeoutSeconds = 90;
    }
}
