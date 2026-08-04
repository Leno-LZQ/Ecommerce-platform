package com.ecommerce.recommendationservice.dto;

import lombok.Data;

/**
 * behavior.* MQ 事件。
 */
@Data
public class BehaviorEvent {

    private String eventId;
    private Long timestamp;
    private String traceId;

    private Long userId;
    private Long productId;
    private String behaviorType;
    private String sessionId;
    private String searchKeyword;
    private Integer durationMs;

}
