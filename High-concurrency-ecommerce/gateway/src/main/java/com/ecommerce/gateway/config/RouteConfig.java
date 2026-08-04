package com.ecommerce.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // ===== 认证与用户 =====
            .route("user-service", r -> r
                .path("/api/auth/**", "/api/users/**")
                .uri("lb://user-service"))

            // ===== 权限管理（user-service 提供） =====
            .route("user-admin-permissions", r -> r
                .path("/api/admin/roles/**", "/api/admin/permissions/**", "/api/admin/users/**")
                .uri("lb://user-service"))

            // ===== 商品（公开读） =====
            .route("product-service", r -> r
                .path("/api/products/**", "/api/categories/**")
                .uri("lb://product-service"))

            // ===== 购物车 =====
            .route("cart-service", r -> r
                .path("/api/cart/**")
                .uri("lb://cart-service"))

            // ===== 订单（核心交易） =====
            .route("order-service", r -> r
                .path("/api/orders/**")
                .uri("lb://order-service"))

            // ===== 支付（安全隔离） =====
            .route("payment-service", r -> r
                .path("/api/payment/**")
                .uri("lb://payment-service"))

            // ===== 搜索（公开，高频） =====
            .route("search-service", r -> r
                .path("/api/search/**")
                .uri("lb://search-service"))

            // ===== 商家 =====
            .route("merchant-service", r -> r
                .path("/api/v1/merchant/**", "/api/v1/admin/merchants/**")
                .uri("lb://merchant-service"))

            // ===== 结算 =====
            .route("settlement-service", r -> r
                .path("/api/settlement/**")
                .uri("lb://settlement-service"))

            // ===== 优惠引擎 =====
            .route("promotion-service", r -> r
                .path("/api/promotion/**")
                .uri("lb://promotion-service"))

            // ===== 促销管理（promotion-service 提供） =====
            .route("promotion-admin", r -> r
                .path("/api/admin/promotion/**")
                .uri("lb://promotion-service"))

            // ===== 推荐 =====
            .route("recommendation-service", r -> r
                .path("/api/recommend/**")
                .uri("lb://recommendation-service"))

            // ===== 消息通信（含 WebSocket） =====
            .route("message-service", r -> r
                .path("/api/messages/**")
                .uri("lb://message-service"))

            // ===== 客服（含 WebSocket） =====
            .route("cs-service", r -> r
                .path("/api/cs/**")
                .uri("lb://cs-service"))

            // ===== 管理后台 BFF =====
            .route("admin-service", r -> r
                .path("/api/admin/**")
                .uri("lb://admin-service"))

            // ===== WebSocket 透明代理 =====
            .route("ws-messages", r -> r
                .path("/ws/messages/**")
                .uri("lb:ws://message-service"))

            .route("ws-cs", r -> r
                .path("/ws/cs/**")
                .uri("lb:ws://cs-service"))

            .build();
    }
}
