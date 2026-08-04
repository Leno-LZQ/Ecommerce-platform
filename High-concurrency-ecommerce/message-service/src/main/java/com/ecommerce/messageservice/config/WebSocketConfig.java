package com.ecommerce.messageservice.config;

import com.ecommerce.messageservice.websocket.MessageWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer{

    private final MessageWebSocketHandler messageWebSocketHandler;
    private final HandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(MessageWebSocketHandler messageWebSocketHandler,
                           HandshakeInterceptor handshakeInterceptor) {
        this.messageWebSocketHandler = messageWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }


    /**
     * 注册 WebSocket 处理器
     *
     * 相当于告诉服务器：
     *   "当有人请求 ws://服务器地址/ws/messages 时，
     *    让 messageWebSocketHandler 来处理"
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        registry
            .addHandler(messageWebSocketHandler,"/ws/messages")
            .addInterceptors(handshakeInterceptor)
            .setAllowedOrigins("*");
    }
}
