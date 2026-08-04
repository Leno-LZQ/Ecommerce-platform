package com.ecommerce.productservice.service.impl;


import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.client.OrderClient;
import com.ecommerce.client.UserClient;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.dto.order.OrderDTO;
import com.ecommerce.dto.user.UserProfileResponse;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.productservice.dto.ReviewResponse;
import com.ecommerce.productservice.dto.ReviewSubmitRequest;
import com.ecommerce.productservice.dto.ReviewSummaryResponse;
import com.ecommerce.productservice.entity.Review;
import com.ecommerce.productservice.mapper.ReviewMapper;
import com.ecommerce.productservice.service.ReviewService;
import com.ecommerce.result.Result;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    @Autowired private ReviewMapper reviewMapper;
    @Autowired private OrderClient orderClient;   // Feign → order-service
    @Autowired private UserClient userClient;     //Feign → user-service
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private Cache<Long, UserProfileResponse> userCache;

    @Override
    public IPage<ReviewResponse> pageByProduct(Long productId, int page, int size, Integer score) {

        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
            .eq(Review::getProductId, productId)
            .eq(Review::getStatus, 1)
            .orderByDesc(Review::getCreateTime);

        if (score != null && score >= 1 && score <= 5) {
            wrapper.eq(Review::getScore, score);
        }

        Page<Review> reviewPage = new Page<>(page, size);
        Page<Review> result = reviewMapper.selectPage(reviewPage, wrapper);

        // ① 收集所有 userId，去重
        Set<Long> userIds = result.getRecords().stream()
            .map(Review::getUserId)
            .collect(Collectors.toSet());

        Map<Long,UserProfileResponse> profileMap = new HashMap<>();
        List<Long> missed = new ArrayList<>();
        for(Long id : userIds){
            UserProfileResponse cached = userCache.getIfPresent(id);
            if(cached!=null){
                profileMap.put(id, cached);
            }else{
                missed.add(id);
            }
        }

        if(!missed.isEmpty()){
            Map<Long,UserProfileResponse> batch = userClient.getUsersByIds(missed).getData();
            for (var e : batch.entrySet()) {
                userCache.put(e.getKey(), e.getValue());
                profileMap.put(e.getKey(), e.getValue());
            }
        }
        // ③ 转换时从 Map 取
        return result.convert(r -> toResponse(r, profileMap));
    }

    @Override
    @Transactional
    public ReviewResponse submit(Long productId, ReviewSubmitRequest request, Long userId) {

        // ① Feign 调 order-service，校验订单归属 + 状态
        Result<OrderDTO> orderResult = orderClient.getOrderByNo(request.getOrderNo());
        if (orderResult == null || orderResult.getData() == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        OrderDTO order = orderResult.getData();
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 3) {  // 3 = 已完成
            throw new BusinessException(ErrorCode.REVIEW_ORDER_NOT_COMPLETED);
        }

        // ② 防重复评价
        Long existingCount = reviewMapper.selectCount(
            new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderNo, request.getOrderNo())
                .eq(Review::getSkuId, request.getSkuId())
        );
        if (existingCount > 0) {
            throw new BusinessException(ErrorCode.REVIEW_DUPLICATE);
        }

        // ③ 评分校验
        if (request.getScore() < 1 || request.getScore() > 5) {
            throw new BusinessException(ErrorCode.REVIEW_SCORE_INVALID);
        }

        // ④ 插入
        Review review = new Review();
        review.setId(IdUtil.getSnowflakeNextId());  // 雪花ID
        review.setProductId(productId);
        review.setSkuId(request.getSkuId());
        review.setUserId(userId);
        review.setScore(request.getScore());
        review.setContent(request.getContent());
        review.setImages(request.getImages());
        review.setStatus(1);                       // 自动审核通过（P1简化）
        review.setCreateTime(LocalDateTime.now());
        reviewMapper.insert(review);

        // ⑤ 清评价汇总缓存
        redisTemplate.delete("review:summary:" + productId);

        log.info("评价提交成功: productId={}, userId={}, score={}", productId, userId, request.getScore());

        // ⑥ 返回
        ReviewResponse vo = new ReviewResponse();
        BeanUtils.copyProperties(review, vo);
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }

    @Override
    public ReviewSummaryResponse getSummary(Long productId) {
        // ① 查缓存
        String cacheKey = "review:summary:" + productId;
        ReviewSummaryResponse cached = (ReviewSummaryResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // ② GROUP BY 查询
        List<Map<String, Object>> rows = reviewMapper.selectMaps(
            new QueryWrapper<Review>()
                .select("score, COUNT(*) as count")
                .eq("product_id", productId)
                .eq("status", 1)
                .groupBy("score")
        );

        // ③ 构建分布
        Map<Integer, Integer> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) distribution.put(i, 0);

        int totalCount = 0;
        int goodCount = 0;
        BigDecimal sumScore = BigDecimal.ZERO;

        for (Map<String, Object> row : rows) {
            int score = ((Number) row.get("score")).intValue();
            int count = ((Number) row.get("count")).intValue();
            distribution.put(score, count);
            sumScore = sumScore.add(BigDecimal.valueOf((long) score * count));
            totalCount += count;
            if (score >= 4) goodCount += count;
        }

        // ④ 组装
        ReviewSummaryResponse vo = new ReviewSummaryResponse();
        vo.setScoreDistribution(distribution);
        vo.setTotalCount(totalCount);
        vo.setAverageScore(totalCount > 0
            ? sumScore.divide(BigDecimal.valueOf(totalCount), 1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO);
        vo.setGoodRate(totalCount > 0 ? goodCount * 100 / totalCount : 0);

        // ⑤ 写缓存（5 分钟）
        redisTemplate.opsForValue().set(cacheKey, vo, 5, TimeUnit.MINUTES);
        return vo;
    }

    private ReviewResponse toResponse(Review r, Map<Long, UserProfileResponse> profileMap) {
        ReviewResponse vo = new ReviewResponse();
        BeanUtils.copyProperties(r, vo);

        UserProfileResponse profile = profileMap.get(r.getUserId());
        if (profile != null) {
            vo.setUserNickname(profile.getNickname());
            vo.setUserAvatar(profile.getAvatar());
        }
        return vo;
    }
}
