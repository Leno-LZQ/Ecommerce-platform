package com.ecommerce.productservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {
    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    private String host;
    private String dirPrefix = "products/";
    private String callbackUrl;
    private Integer expireSeconds = 60;
    private Integer maxSizeMb = 5;
}
