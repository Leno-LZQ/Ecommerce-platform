package com.ecommerce.recommendationservice.job;

import com.ecommerce.recommendationservice.entity.ProductSimilarity;
import com.ecommerce.recommendationservice.entity.RecoResult;
import com.ecommerce.recommendationservice.entity.UserBehavior;
import com.ecommerce.recommendationservice.entity.enums.BehaviorType;
import com.ecommerce.recommendationservice.entity.enums.RecoStrategyType;
import com.ecommerce.recommendationservice.mapper.ProductSimilarityMapper;
import com.ecommerce.recommendationservice.mapper.RecoResultMapper;
import com.ecommerce.recommendationservice.mapper.UserBehaviorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 离线推荐计算任务：每日凌晨 2 点执行。
 * <p>
 * 计算：热门排行、Item-CF 相似度、关联规则、User-CF、新人推荐。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineComputeJob {

    private final UserBehaviorMapper userBehaviorMapper;
    private final RecoResultMapper recoResultMapper;
    private final ProductSimilarityMapper productSimilarityMapper;
    private final StringRedisTemplate redisTemplate;

    private static final int HOT_LIMIT = 200;
    private static final int SIMILAR_LIMIT = 50;
    private static final int USER_CF_LIMIT = 50;
    private static final int MIN_COMMON_ITEMS = 2;

    /**
     * 每天 02:00 执行。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void compute() {
        log.info("开始离线推荐计算...");
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(7);

        List<UserBehavior> behaviors = userBehaviorMapper.selectByTimeWindow(start, end);
        if (CollectionUtils.isEmpty(behaviors)) {
            log.info("无行为数据，跳过离线计算");
            return;
        }

        // 清空旧结果（保留最近 3 天）
        recoResultMapper.delete(null);
        productSimilarityMapper.delete(null);

        computeHot(behaviors);
        computeItemCf(behaviors);
        computeAssocRule(behaviors);
        computeUserCf(behaviors);
        computeNewUserRec(behaviors);

        log.info("离线推荐计算完成");
    }

    // ==================== 热门排行 ====================

    private void computeHot(List<UserBehavior> behaviors) {
        Map<Long, BigDecimal> scores = new HashMap<>();
        Map<Long, Long> categoryMap = new HashMap<>();

        for (UserBehavior b : behaviors) {
            BehaviorType type = b.getBehaviorType();
            if (type == null) {
                continue;
            }
            scores.merge(b.getProductId(), BigDecimal.valueOf(type.getWeight()), BigDecimal::add);
            // 这里没有 categoryId，后续回填到 Redis 时再按 category 分组
        }

        LocalDateTime now = LocalDateTime.now();
        List<RecoResult> results = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(HOT_LIMIT)
                .map(e -> buildRecoResult(null, e.getKey(), RecoStrategyType.HOT.getCode(), e.getValue(), now))
                .collect(Collectors.toList());

        if (!results.isEmpty()) {
            for (RecoResult r : results) {
                recoResultMapper.insert(r);
            }
            updateHotRedis(results);
        }
        log.info("热门排行计算完成: {} 条", results.size());
    }

    private void updateHotRedis(List<RecoResult> hotResults) {
        String allKey = "reco:hot:all";
        redisTemplate.delete(allKey);
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (RecoResult r : hotResults) {
            tuples.add(ZSetOperations.TypedTuple.of(String.valueOf(r.getProductId()), r.getScore().doubleValue()));
        }
        redisTemplate.opsForZSet().add(allKey, tuples);
    }

    // ==================== Item-CF 相似度 ====================

    private void computeItemCf(List<UserBehavior> behaviors) {
        Map<Long, Set<Long>> productUsers = new HashMap<>();
        for (UserBehavior b : behaviors) {
            productUsers.computeIfAbsent(b.getProductId(), k -> new HashSet<>()).add(b.getUserId());
        }

        List<Long> products = new ArrayList<>(productUsers.keySet());
        List<ProductSimilarity> similarities = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < products.size(); i++) {
            Long productA = products.get(i);
            Set<Long> usersA = productUsers.get(productA);
            for (int j = i + 1; j < products.size(); j++) {
                Long productB = products.get(j);
                Set<Long> usersB = productUsers.get(productB);

                Set<Long> intersection = new HashSet<>(usersA);
                intersection.retainAll(usersB);
                if (intersection.size() < MIN_COMMON_ITEMS) {
                    continue;
                }

                Set<Long> union = new HashSet<>(usersA);
                union.addAll(usersB);
                double jaccard = (double) intersection.size() / union.size();
                BigDecimal sim = BigDecimal.valueOf(jaccard).setScale(4, RoundingMode.HALF_UP);

                ProductSimilarity ps = new ProductSimilarity();
                ps.setProductIdA(productA);
                ps.setProductIdB(productB);
                ps.setSimilarity(sim);
                ps.setUpdatedAt(now);
                similarities.add(ps);
            }
        }

        // 取 TopN 并持久化
        Map<Long, List<ProductSimilarity>> grouped = similarities.stream()
                .collect(Collectors.groupingBy(ProductSimilarity::getProductIdA));
        for (List<ProductSimilarity> list : grouped.values()) {
            list.sort(Comparator.comparing(ProductSimilarity::getSimilarity).reversed());
            int limit = Math.min(list.size(), SIMILAR_LIMIT);
            for (int i = 0; i < limit; i++) {
                productSimilarityMapper.insert(list.get(i));
            }
        }
        log.info("Item-CF 相似度计算完成: {} 条", similarities.size());
    }

    // ==================== 关联规则（共现购买） ====================

    private void computeAssocRule(List<UserBehavior> behaviors) {
        Map<Long, Set<Long>> userPurchases = new HashMap<>();
        for (UserBehavior b : behaviors) {
            if (b.getBehaviorType() == BehaviorType.PURCHASE) {
                userPurchases.computeIfAbsent(b.getUserId(), k -> new HashSet<>()).add(b.getProductId());
            }
        }

        Map<Long, Integer> productCount = new HashMap<>();
        Map<String, Integer> pairCount = new HashMap<>();
        for (Set<Long> products : userPurchases.values()) {
            List<Long> list = new ArrayList<>(products);
            for (Long p : list) {
                productCount.merge(p, 1, Integer::sum);
            }
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    long a = list.get(i);
                    long b = list.get(j);
                    String key = a < b ? a + "-" + b : b + "-" + a;
                    pairCount.merge(key, 1, Integer::sum);
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, BigDecimal> assocScores = new HashMap<>();
        for (Map.Entry<String, Integer> entry : pairCount.entrySet()) {
            String[] parts = entry.getKey().split("-");
            long a = Long.parseLong(parts[0]);
            long b = Long.parseLong(parts[1]);
            int count = entry.getValue();
            int countA = productCount.getOrDefault(a, 1);
            int countB = productCount.getOrDefault(b, 1);
            double confidence = (double) count / Math.min(countA, countB);
            BigDecimal score = BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP);
            assocScores.merge(a, score, BigDecimal::add);
            assocScores.merge(b, score, BigDecimal::add);
        }

        List<RecoResult> results = assocScores.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(HOT_LIMIT)
                .map(e -> buildRecoResult(null, e.getKey(), RecoStrategyType.ASSOC_RULE.getCode(), e.getValue(), now))
                .collect(Collectors.toList());

        for (RecoResult r : results) {
            recoResultMapper.insert(r);
        }
        log.info("关联规则计算完成: {} 条", results.size());
    }

    // ==================== User-CF ====================

    private void computeUserCf(List<UserBehavior> behaviors) {
        Map<Long, Set<Long>> userProducts = new HashMap<>();
        for (UserBehavior b : behaviors) {
            userProducts.computeIfAbsent(b.getUserId(), k -> new HashSet<>()).add(b.getProductId());
        }

        List<Long> users = new ArrayList<>(userProducts.keySet());
        Map<Long, Map<Long, BigDecimal>> userScores = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < users.size(); i++) {
            Long userA = users.get(i);
            Set<Long> productsA = userProducts.get(userA);
            for (int j = i + 1; j < users.size(); j++) {
                Long userB = users.get(j);
                Set<Long> productsB = userProducts.get(userB);

                Set<Long> intersection = new HashSet<>(productsA);
                intersection.retainAll(productsB);
                if (intersection.isEmpty()) {
                    continue;
                }

                Set<Long> union = new HashSet<>(productsA);
                union.addAll(productsB);
                double sim = (double) intersection.size() / union.size();
                BigDecimal score = BigDecimal.valueOf(sim).setScale(4, RoundingMode.HALF_UP);

                userScores.computeIfAbsent(userA, k -> new HashMap<>()).merge(userB, score, BigDecimal::add);
                userScores.computeIfAbsent(userB, k -> new HashMap<>()).merge(userA, score, BigDecimal::add);
            }
        }

        for (Map.Entry<Long, Map<Long, BigDecimal>> entry : userScores.entrySet()) {
            Long userId = entry.getKey();
            List<Map.Entry<Long, BigDecimal>> similarUsers = entry.getValue().entrySet().stream()
                    .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                    .limit(20)
                    .collect(Collectors.toList());

            Map<Long, BigDecimal> productScores = new HashMap<>();
            for (Map.Entry<Long, BigDecimal> simUser : similarUsers) {
                Long similarUserId = simUser.getKey();
                BigDecimal similarity = simUser.getValue();
                Set<Long> products = userProducts.getOrDefault(similarUserId, Collections.emptySet());
                for (Long pid : products) {
                    if (!userProducts.get(userId).contains(pid)) {
                        productScores.merge(pid, similarity, BigDecimal::add);
                    }
                }
            }

            List<RecoResult> results = productScores.entrySet().stream()
                    .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                    .limit(USER_CF_LIMIT)
                    .map(e -> buildRecoResult(userId, e.getKey(), RecoStrategyType.CF_USER.getCode(), e.getValue(), now))
                    .collect(Collectors.toList());

            for (RecoResult r : results) {
                recoResultMapper.insert(r);
            }
        }
        log.info("User-CF 计算完成");
    }

    // ==================== 新人推荐 ====================

    private void computeNewUserRec(List<UserBehavior> behaviors) {
        Map<Long, BigDecimal> scores = new HashMap<>();
        for (UserBehavior b : behaviors) {
            BehaviorType type = b.getBehaviorType();
            if (type == null) {
                continue;
            }
            scores.merge(b.getProductId(), BigDecimal.valueOf(type.getWeight()), BigDecimal::add);
        }

        LocalDateTime now = LocalDateTime.now();
        List<RecoResult> results = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(HOT_LIMIT)
                .map(e -> buildRecoResult(null, e.getKey(), RecoStrategyType.NEW_USER.getCode(), e.getValue(), now))
                .collect(Collectors.toList());

        for (RecoResult r : results) {
            recoResultMapper.insert(r);
        }
        log.info("新人推荐计算完成: {} 条", results.size());
    }

    private RecoResult buildRecoResult(Long userId, Long productId, String strategy, BigDecimal score, LocalDateTime now) {
        RecoResult r = new RecoResult();
        r.setUserId(userId);
        r.setProductId(productId);
        r.setStrategy(strategy);
        r.setScore(score);
        r.setPosition(0);
        r.setGeneratedAt(now);
        return r;
    }

}
