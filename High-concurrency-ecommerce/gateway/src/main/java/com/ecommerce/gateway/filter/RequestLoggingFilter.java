package com.ecommerce.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;



@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String traceId = MDC.get("traceId");

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long elapsed = System.currentTimeMillis() - startTime;
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            int code = status != null ? status.value() : 0;

            if (code >= 500) {
                log.error("[{}] {} {} → {} ({}ms) [SERVER ERROR]",
                    traceId, method, path, code, elapsed);
            } else if (code >= 400) {
                log.warn("[{}] {} {} → {} ({}ms) [CLIENT ERROR]",
                    traceId, method, path, code, elapsed);
            } else {
                log.info("[{}] {} {} → {} ({}ms)",
                    traceId, method, path, code, elapsed);
            }
        }));
    }

    @Override
    public int getOrder() {
        return 1;  // 在所有过滤器之后执行
    }
}
