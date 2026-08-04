package com.ecommerce.dto.recommendation;

import lombok.Data;

/**
 * 用户行为事件 DTO（供 MQ 或 Feign 透传）。
 */
@Data
public class UserBehaviorEventDTO {

    private Long userId;
    private Long productId;
    private String behaviorType;
    private String sessionId;
    private String searchKeyword;
    private Integer durationMs;

}
