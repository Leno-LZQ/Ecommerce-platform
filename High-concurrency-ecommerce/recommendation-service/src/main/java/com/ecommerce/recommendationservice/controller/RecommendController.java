package com.ecommerce.recommendationservice.controller;

import com.ecommerce.recommendationservice.service.RecommendationService;
import com.ecommerce.recommendationservice.vo.RecommendItemVO;
import com.ecommerce.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C 端推荐 API。
 */
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendationService recommendationService;

    @GetMapping("/home")
    public Result<List<RecommendItemVO>> home(@RequestParam Long userId,
                                               @RequestParam(required = false) Integer size) {
        return Result.success(recommendationService.recommendForHome(userId, size));
    }

    @GetMapping("/similar")
    public Result<List<RecommendItemVO>> similar(@RequestParam Long productId,
                                                  @RequestParam(required = false) Integer size) {
        return Result.success(recommendationService.recommendSimilar(productId, size));
    }

    @GetMapping("/assoc")
    public Result<List<RecommendItemVO>> assoc(@RequestParam Long productId,
                                                @RequestParam(required = false) Integer size) {
        return Result.success(recommendationService.recommendAssociated(productId, size));
    }

    @GetMapping("/hot")
    public Result<List<RecommendItemVO>> hot(@RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) Integer size) {
        return Result.success(recommendationService.recommendHot(categoryId, size));
    }

}
