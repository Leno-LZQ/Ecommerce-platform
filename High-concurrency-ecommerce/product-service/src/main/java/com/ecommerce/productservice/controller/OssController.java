package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.service.OssService;
import com.ecommerce.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/products/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssService ossService;

    /**
     * 获取 OSS 直传签名
     * 前端在每次上传前调用此接口获取临时凭证
     */
    @GetMapping("/signature")
    public Result<Map<String, String>> signature() {
        return Result.success(ossService.generatePostSignature());
    }
}
//%YAwZGV4xog!q6}e52ZtNoPgOkNkRj|B
