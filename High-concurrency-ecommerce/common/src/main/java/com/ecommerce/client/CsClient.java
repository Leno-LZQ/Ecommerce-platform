package com.ecommerce.client;

import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 客服服务 Feign 客户端
 */
@FeignClient(name = "cs-service")
public interface CsClient {

    @GetMapping("/internal/cs/health")
    Result<String> health();
}
