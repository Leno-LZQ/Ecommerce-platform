package com.ecommerce.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);

        // 如果请求中已有 TraceId（由上游 Nginx 或前端传入），则复用
        // 否则生成一个新的 UUID
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 写入 MDC → 当前线程的所有日志自动携带此 TraceId
        MDC.put("traceId", traceId);

        // 写入响应头 → 前端和下游服务都能获取
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);

        // 如果前端没有传，也写回请求头（下游 Feign 调用时会自动传递）
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header(TRACE_ID_HEADER, traceId)
            .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
            .doFinally(signalType -> MDC.clear());  // 请求结束后清理 MDC
    }

    @Override
    public int getOrder() {
        return -3;
    }
}
