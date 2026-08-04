package com.ecommerce.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class SentinelConfig {

    @PostConstruct
    public void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        rules.add(new GatewayFlowRule("user-service")
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
            .setCount(500).setIntervalSec(1));

        rules.add(new GatewayFlowRule("product-service")
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
            .setCount(2000).setIntervalSec(1));

        rules.add(new GatewayFlowRule("cart-service")
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
            .setCount(1000).setIntervalSec(1));

        rules.add(new GatewayFlowRule("order-service")
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
            .setCount(1000).setIntervalSec(1));

        rules.add(new GatewayFlowRule("payment-service")
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
            .setCount(500).setIntervalSec(1));

        rules.add(new GatewayFlowRule("search-service")
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
            .setCount(3000).setIntervalSec(1));

        rules.add(new GatewayFlowRule("promotion-service")
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
            .setCount(1000).setIntervalSec(1));

        rules.add(new GatewayFlowRule("recommendation-service")
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
            .setCount(2000).setIntervalSec(1));

        rules.add(new GatewayFlowRule("admin-service")
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
            .setCount(200).setIntervalSec(1));

        GatewayRuleManager.loadRules(rules);
    }

    @PostConstruct
    public void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler((exchange, t) ->
            ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":429,\"message\":\"系统繁忙，请稍后再试\"}")
        );
    }
}
