package com.ecommerce.recommendationservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 接收前端或网关上报的用户行为。
 */
@Data
public class UserBehaviorRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long productId;

    @NotNull
    private String behaviorType;

    private String sessionId;
    private String searchKeyword;
    private Integer durationMs;

}
