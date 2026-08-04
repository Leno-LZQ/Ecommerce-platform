package com.ecommerce.csservice.controller;

import com.ecommerce.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服服务内部接口（供其他服务调用）
 */
@RestController
@RequestMapping("/internal/cs")
public class CsInternalController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("ok");
    }
}
