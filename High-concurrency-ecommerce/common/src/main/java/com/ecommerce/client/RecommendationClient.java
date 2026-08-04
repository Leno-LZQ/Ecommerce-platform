package com.ecommerce.client;

import com.ecommerce.dto.recommendation.UserBehaviorEventDTO;
import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * recommendation-service 内部接口。
 */
@FeignClient(name = "recommendation-service")
public interface RecommendationClient {

    /**
     * 上报用户行为（供 gateway 或其他服务转发前端埋点）。
     */
    @PostMapping("/internal/recommend/behavior")
    Result<Void> reportBehavior(@RequestBody UserBehaviorEventDTO event);

}
