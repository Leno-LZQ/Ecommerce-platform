package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.properties.AppGatewayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final StringRedisTemplate redis;
    private final List<String> whitelist;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthGlobalFilter(StringRedisTemplate redis,
                            AppGatewayProperties properties) {
        this.redis = redis;
        this.whitelist = properties.getWhitelist();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isWhitelisted(exchange)) {
            return chain.filter(exchange);
        }

        String sessionId = extractSessionId(exchange);
        if (sessionId == null) {
            return writeJsonResponse(exchange, HttpStatus.UNAUTHORIZED,
                "{\"code\":401,\"message\":\"未登录\"}");
        }

        String json = redis.opsForValue().get("session:" + sessionId);
        if (json == null) {
            return writeJsonResponse(exchange, HttpStatus.UNAUTHORIZED,
                "{\"code\":401,\"message\":\"会话已过期，请重新登录\"}");
        }

        JsonNode session;
        try {
            session = mapper.readTree(json);
        } catch (Exception e) {
            return writeJsonResponse(exchange, HttpStatus.UNAUTHORIZED,
                "{\"code\":401,\"message\":\"会话数据异常\"}");
        }

        Long userId = session.get("userId").asLong();

        List<String> roleList = new ArrayList<>();
        session.get("roles").forEach(n -> roleList.add(n.asText()));

        redis.expire("session:" + sessionId, Duration.ofMinutes(30));

        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
            .header("X-User-Id", String.valueOf(userId))
            .header("X-Roles", String.join(",", roleList));

        if (session.has("merchantId") && !session.get("merchantId").isNull()) {
            builder.header("X-Merchant-Id",
                String.valueOf(session.get("merchantId").asLong()));
        }
        if (session.has("permissions") && !session.get("permissions").isNull()) {
            List<String> perms = new ArrayList<>();
            session.get("permissions").forEach(n -> perms.add(n.asText()));
            builder.header("X-Permissions", String.join(",", perms));
        }

        ServerHttpRequest mutated = builder.build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isWhitelisted(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        AntPathMatcher matcher = new AntPathMatcher();
        for (String entry : whitelist) {
            String[] parts = entry.trim().split(" ", 2);
            if (parts.length == 2 && isHttpMethod(parts[0])) {
                if (parts[0].equalsIgnoreCase(method) && matcher.match(parts[1], path)) {
                    return true;
                }
            } else if (matcher.match(entry, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHttpMethod(String s) {
        for (HttpMethod m : HttpMethod.values()) {
            if (m.name().equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    private String extractSessionId(ServerWebExchange exchange) {
        MultiValueMap<String, HttpCookie> cookies =
            exchange.getRequest().getCookies();
        List<HttpCookie> list = cookies.get("sessionId");
        return (list != null && !list.isEmpty()) ? list.get(0).getValue() : null;
    }

    private Mono<Void> writeJsonResponse(ServerWebExchange exchange,
                                         HttpStatus status, String body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders()
            .setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
