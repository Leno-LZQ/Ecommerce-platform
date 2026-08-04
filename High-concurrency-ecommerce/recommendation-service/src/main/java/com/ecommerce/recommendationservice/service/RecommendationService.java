package com.ecommerce.recommendationservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.client.ProductClient;
import com.ecommerce.dto.product.ProductSummaryDTO;
import com.ecommerce.recommendationservice.entity.ProductSimilarity;
import com.ecommerce.recommendationservice.entity.RecoResult;
import com.ecommerce.recommendationservice.entity.RecoStrategy;
import com.ecommerce.recommendationservice.entity.UserBehavior;
import com.ecommerce.recommendationservice.entity.enums.RecoStrategyType;
import com.ecommerce.recommendationservice.mapper.ProductSimilarityMapper;
import com.ecommerce.recommendationservice.mapper.RecoResultMapper;
import com.ecommerce.recommendationservice.mapper.RecoStrategyMapper;
import com.ecommerce.recommendationservice.mapper.UserBehaviorMapper;
import com.ecommerce.recommendationservice.vo.RecommendItemVO;
import com.ecommerce.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 在线推荐服务：多路召回 + 加权排序 + 去重 + 多样性打散。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecoResultMapper recoResultMapper;
    private final ProductSimilarityMapper productSimilarityMapper;
    private final RecoStrategyMapper recoStrategyMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final ProductClient productClient;
    private final StringRedisTemplate redisTemplate;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_CATEGORY_REPEAT = 3;

    private static final String RECENT_KEY = "reco:user:%s:recent";
    private static final String PURCHASED_KEY = "reco:purchased:%s";
    private static final String EXCLUDE_KEY = "reco:exclude:%s";
    private static final String HOT_KEY = "reco:hot:%s";

    /**
     * 首页"猜你喜欢"。
     */
    public List<RecommendItemVO> recommendForHome(Long userId, Integer size) {
        int limit = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : size;

        List<RecoStrategy> strategies = recoStrategyMapper.selectEnabled();
        Map<String, BigDecimal> weights = strategies.stream()
                .collect(Collectors.toMap(RecoStrategy::getStrategyCode, RecoStrategy::getWeight));

        Map<Long, Candidate> candidateMap = new HashMap<>();

        // 1. 协同过滤（用户）
        if (weights.containsKey(RecoStrategyType.CF_USER.getCode())) {
            List<RecoResult> list = recoResultMapper.selectTopByUserAndStrategy(
                    userId, RecoStrategyType.CF_USER.getCode(), 50);
            mergeCandidates(candidateMap, list, weights.get(RecoStrategyType.CF_USER.getCode()));
        }

        // 2. 协同过滤（物品）
        if (weights.containsKey(RecoStrategyType.CF_ITEM.getCode())) {
            List<RecoResult> list = recoResultMapper.selectTopByUserAndStrategy(
                    userId, RecoStrategyType.CF_ITEM.getCode(), 50);
            mergeCandidates(candidateMap, list, weights.get(RecoStrategyType.CF_ITEM.getCode()));
        }

        // 3. 最近行为触发相似商品
        Set<Long> recentProducts = getRecentProducts(userId, 20);
        if (!recentProducts.isEmpty() && weights.containsKey(RecoStrategyType.CF_ITEM.getCode())) {
            BigDecimal weight = weights.get(RecoStrategyType.CF_ITEM.getCode());
            for (Long pid : recentProducts) {
                productSimilarityMapper.selectTopByProductA(pid, 10).forEach(sim ->
                        candidateMap.computeIfAbsent(sim.getProductIdB(),
                                k -> new Candidate(k, RecoStrategyType.CF_ITEM.getCode(), BigDecimal.ZERO))
                                .addScore(sim.getSimilarity().multiply(weight))
                );
            }
        }

        // 4. 新人策略 or 热门兜底
        boolean coldStart = isColdStart(userId);
        if (coldStart && weights.containsKey(RecoStrategyType.NEW_USER.getCode())) {
            List<RecoResult> list = recoResultMapper.selectTopByStrategy(
                    RecoStrategyType.NEW_USER.getCode(), 30);
            mergeCandidates(candidateMap, list, weights.get(RecoStrategyType.NEW_USER.getCode()));
        }

        if (weights.containsKey(RecoStrategyType.HOT.getCode())) {
            List<RecoResult> list = recoResultMapper.selectTopByStrategy(
                    RecoStrategyType.HOT.getCode(), 30);
            mergeCandidates(candidateMap, list, weights.get(RecoStrategyType.HOT.getCode()));
        }

        // 5. 过滤
        Set<Long> filtered = filter(userId, candidateMap.keySet());

        // 6. 排序并取更多候选，供多样性打散
        List<Candidate> sorted = candidateMap.values().stream()
                .filter(c -> filtered.contains(c.productId))
                .sorted(Comparator.comparing(Candidate::getScore).reversed())
                .limit(limit * 2L)
                .collect(Collectors.toList());

        List<Long> productIds = sorted.stream().map(Candidate::getProductId).collect(Collectors.toList());
        List<RecommendItemVO> filled = fillProducts(productIds, candidateMap);

        // 7. 多样性打散
        return diversityReorderByCategory(filled, limit);
    }

    /**
     * 商品详情页"看了又看"。
     */
    public List<RecommendItemVO> recommendSimilar(Long productId, Integer size) {
        int limit = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : size;
        List<Long> productIds = productSimilarityMapper.selectTopByProductA(productId, limit)
                .stream()
                .map(ProductSimilarity::getProductIdB)
                .collect(Collectors.toList());
        if (productIds.isEmpty()) {
            productIds = hotProductIds(null, limit);
        }
        Map<Long, Candidate> candidateMap = new HashMap<>();
        productIds.forEach(pid -> candidateMap.put(pid, new Candidate(pid, RecoStrategyType.CF_ITEM.getCode(), BigDecimal.ZERO)));
        return fillProducts(productIds, candidateMap);
    }

    /**
     * 购物车/下单成功页"买了还买"。
     */
    public List<RecommendItemVO> recommendAssociated(Long productId, Integer size) {
        int limit = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : size;
        List<RecoResult> list = recoResultMapper.selectTopByStrategy(
                RecoStrategyType.ASSOC_RULE.getCode(), limit);
        if (list.isEmpty()) {
            return recommendHot(null, limit);
        }
        Map<Long, Candidate> candidateMap = new HashMap<>();
        mergeCandidates(candidateMap, list, BigDecimal.ONE);
        List<Long> productIds = list.stream().map(RecoResult::getProductId).collect(Collectors.toList());
        return fillProducts(productIds, candidateMap);
    }

    /**
     * 热门商品排行。
     */
    public List<RecommendItemVO> recommendHot(Long categoryId, Integer size) {
        int limit = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : size;
        List<Long> productIds = hotProductIds(categoryId, limit);
        Map<Long, Candidate> candidateMap = new HashMap<>();
        productIds.forEach(pid -> candidateMap.put(pid, new Candidate(pid, RecoStrategyType.HOT.getCode(), BigDecimal.ZERO)));
        return fillProducts(productIds, candidateMap);
    }

    // ==================== 私有方法 ====================

    private void mergeCandidates(Map<Long, Candidate> map, List<RecoResult> results, BigDecimal weight) {
        if (CollectionUtils.isEmpty(results) || weight == null) {
            return;
        }
        for (RecoResult r : results) {
            map.computeIfAbsent(r.getProductId(),
                            k -> new Candidate(k, r.getStrategy(), BigDecimal.ZERO))
                    .addScore(r.getScore().multiply(weight));
        }
    }

    private Set<Long> getRecentProducts(Long userId, int limit) {
        String key = String.format(RECENT_KEY, userId);
        Set<String> set = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
        if (CollectionUtils.isEmpty(set)) {
            return Collections.emptySet();
        }
        return set.stream().map(Long::parseLong).collect(Collectors.toSet());
    }

    private boolean isColdStart(Long userId) {
        Long count = userBehaviorMapper.selectCount(
                new LambdaQueryWrapper<UserBehavior>().eq(UserBehavior::getUserId, userId));
        return count == null || count < 3;
    }

    private Set<Long> filter(Long userId, Set<Long> candidates) {
        Set<Long> purchased = getRedisSet(String.format(PURCHASED_KEY, userId));
        Set<Long> excluded = getRedisSet(String.format(EXCLUDE_KEY, userId));
        return candidates.stream()
                .filter(pid -> !purchased.contains(pid) && !excluded.contains(pid))
                .collect(Collectors.toSet());
    }

    private Set<Long> getRedisSet(String key) {
        Set<String> set = redisTemplate.opsForSet().members(key);
        if (CollectionUtils.isEmpty(set)) {
            return Collections.emptySet();
        }
        return set.stream().map(Long::parseLong).collect(Collectors.toSet());
    }

    private List<RecommendItemVO> diversityReorderByCategory(List<RecommendItemVO> list, int limit) {
        List<RecommendItemVO> result = new ArrayList<>(limit);
        Map<Long, Integer> categoryCount = new HashMap<>();
        LinkedList<RecommendItemVO> pool = new LinkedList<>(list);

        while (!pool.isEmpty() && result.size() < limit) {
            RecommendItemVO selected = null;
            Iterator<RecommendItemVO> it = pool.iterator();
            while (it.hasNext()) {
                RecommendItemVO vo = it.next();
                long category = vo.getCategoryId() == null ? 0L : vo.getCategoryId();
                if (categoryCount.getOrDefault(category, 0) < MAX_CATEGORY_REPEAT) {
                    selected = vo;
                    it.remove();
                    break;
                }
            }
            if (selected == null) {
                selected = pool.pollFirst();
            }
            if (selected == null) {
                break;
            }
            long category = selected.getCategoryId() == null ? 0L : selected.getCategoryId();
            categoryCount.merge(category, 1, Integer::sum);
            result.add(selected);
        }
        return result;
    }

    private List<Long> hotProductIds(Long categoryId, int limit) {
        String key = String.format(HOT_KEY, categoryId == null ? "all" : categoryId);
        Set<String> set = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
        if (!CollectionUtils.isEmpty(set)) {
            return set.stream().map(Long::parseLong).collect(Collectors.toList());
        }
        List<RecoResult> list = recoResultMapper.selectTopByStrategy(RecoStrategyType.HOT.getCode(), limit);
        return list.stream().map(RecoResult::getProductId).collect(Collectors.toList());
    }

    private List<RecommendItemVO> fillProducts(List<Long> productIds, Map<Long, Candidate> candidateMap) {
        if (CollectionUtils.isEmpty(productIds)) {
            return Collections.emptyList();
        }
        Result<Map<Long, ProductSummaryDTO>> result = productClient.getProductsByIds(productIds);
        if (result == null || result.getData() == null) {
            return Collections.emptyList();
        }
        Map<Long, ProductSummaryDTO> productMap = result.getData();
        List<RecommendItemVO> vos = new ArrayList<>();
        for (Long pid : productIds) {
            ProductSummaryDTO p = productMap.get(pid);
            if (p == null || p.getStatus() == null || p.getStatus() != 1) {
                continue;
            }
            Candidate c = candidateMap.getOrDefault(pid, new Candidate(pid, RecoStrategyType.HOT.getCode(), BigDecimal.ZERO));
            RecommendItemVO vo = RecommendItemVO.builder()
                    .productId(pid)
                    .productName(p.getName())
                    .mainImage(p.getMainImage())
                    .merchantId(p.getMerchantId())
                    .categoryId(p.getCategoryId())
                    .minPrice(p.getMinPrice())
                    .maxPrice(p.getMaxPrice())
                    .strategy(c.strategy)
                    .score(c.score.setScale(2, RoundingMode.HALF_UP))
                    .build();
            vos.add(vo);
        }
        return vos;
    }

    private static class Candidate {
        Long productId;
        String strategy;
        BigDecimal score;

        Candidate(Long productId, String strategy, BigDecimal score) {
            this.productId = productId;
            this.strategy = strategy;
            this.score = score;
        }

        void addScore(BigDecimal delta) {
            this.score = this.score.add(delta);
        }

        BigDecimal getScore() {
            return score;
        }

        Long getProductId() {
            return productId;
        }
    }
}
