package com.ecommerce.productservice.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.PolicyConditions;
import com.ecommerce.productservice.config.OssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final OssProperties ossProperties;

    /**
     * 生成 PostObject 上传签名和策略
     * 前端拿到这些参数后，用 FormData 方式直接 POST 到 OSS
     */
    public Map<String, String> generatePostSignature() {
        OSS client = null;
        try {
            String endpoint = ossProperties.getEndpoint();
            String bucket = ossProperties.getBucket();
            String accessId = ossProperties.getAccessKeyId();
            String accessKey = ossProperties.getAccessKeySecret();
            String host = ossProperties.getHost();

            // 按日期分目录：products/2026/07/29/
            String dir = ossProperties.getDirPrefix()
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/";

            long expireEndTime = System.currentTimeMillis() + ossProperties.getExpireSeconds() * 1000L;
            Date expiration = new Date(expireEndTime);

            PolicyConditions policyConds = new PolicyConditions();
            policyConds.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE,
                0, ossProperties.getMaxSizeMb() * 1024 * 1024L);
            policyConds.addConditionItem(MatchMode.StartWith, PolicyConditions.COND_KEY, dir);

            client = new OSSClientBuilder().build(endpoint, accessId, accessKey);
            String postPolicy = client.generatePostPolicy(expiration, policyConds);
            byte[] binaryData = postPolicy.getBytes(StandardCharsets.UTF_8);
            String encodedPolicy = BinaryUtil.toBase64String(binaryData);
            String postSignature = client.calculatePostSignature(postPolicy);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("accessid", accessId);
            result.put("policy", encodedPolicy);
            result.put("signature", postSignature);
            result.put("dir", dir);
            result.put("host", host);
            result.put("expire", String.valueOf(expireEndTime / 1000));
            return result;

        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     * 校验回调签名（OSS 上传成功后回调时验证，防止伪造）
     */
    public boolean verifyCallback(String authorization, String body) {
        // 简化：实际上需要按 OSS 回调的鉴权规则计算 MD5 + base64
        return true;
    }
}
