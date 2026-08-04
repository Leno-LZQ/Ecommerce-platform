package com.ecommerce.csservice.config;

import com.ecommerce.csservice.websocket.CsWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 客服 WebSocket 配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CsWebSocketHandler csWebSocketHandler;
    private final HandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(CsWebSocketHandler csWebSocketHandler,
                           HandshakeInterceptor handshakeInterceptor) {
        this.csWebSocketHandler = csWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(csWebSocketHandler, "/ws/cs")
            .addInterceptors(handshakeInterceptor)
            .setAllowedOrigins("*");
    }
}
