package com.ecommerce.promotionservice.service;

import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.promotionservice.config.PromotionProperties;
import com.ecommerce.dto.promotion.PromotionLockRequest;
import com.ecommerce.dto.promotion.PromotionLockResponse;
import com.ecommerce.dto.promotion.RefundRequest;
import com.ecommerce.promotionservice.engine.CalcItemState;
import com.ecommerce.promotionservice.engine.CalcMode;
import com.ecommerce.promotionservice.engine.PromotionContext;
import com.ecommerce.promotionservice.entity.CouponUser;
import com.ecommerce.promotionservice.entity.OrderCoupon;
import com.ecommerce.promotionservice.entity.PromotionActivity;
import com.ecommerce.promotionservice.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 优惠锁定服务——负责券的原子锁定、预算预扣、每日计数、核销/释放/退款。
 *
 * 核心理念：DiscountEngine 只管"算"，PromotionLockService 只管"锁"和"释放"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionLockService {

    // ===== 计算层 =====
    private final DiscountEngine discountEngine;

    // ===== 数据层 =====
    private final CouponUserMapper couponUserMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final PromotionActivityMapper activityMapper;
    private final OrderCouponMapper orderCouponMapper;

    // ===== Redis & 序列化 =====
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> lockPromotionScript;
    private final ObjectMapper objectMapper;

    // ===== 配置 =====
    private final PromotionProperties props;

    // ===== 常量 =====
    private static final String HASH_KEY_PREFIX = "promo:lock:order:";
    private static final String COUPON_LOCK_PREFIX = "coupon:lock:";
    private static final String BUDGET_PREFIX = "promo:budget:";
    private static final String DAILY_PREFIX = "promo:daily:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ============================================================
    // 1. lock —— 下单锁定（最核心）
    // ============================================================

    /**
     * 下单优惠锁定。
     *
     * 流程：幂等检查 → 五层计算 → Lua 原子锁 → 写 Hash → DB 更新。
     * 任何一步失败都会补偿已执行的 Redis 写操作。
     */
    @Transactional
    public PromotionLockResponse lock(PromotionLockRequest req) {
        String orderNo = req.getOrderNo();
        String hashKey = HASH_KEY_PREFIX + orderNo;

        // === Step 1: 幂等闸门 ===
        Boolean exists = redisTemplate.hasKey(hashKey);
        if (Boolean.TRUE.equals(exists)) {
            log.info("订单 {} 优惠已锁定，幂等返回历史结果", orderNo);
            return readLockResponseFromRedis(orderNo);
        }

        // === Step 2: 调用 DiscountEngine 计算（只读，无副作用）===
        PromotionContext ctx = discountEngine.calculate(
            req.getUserId(), req.getCouponCodes(), req.getItems(), CalcMode.LOCK);

        List<String> winningCodes = ctx.getWinningCoupons().stream()
            .map(PromotionContext.CouponCandidate::getCouponCode)
            .toList();

        // === Step 3: 准备 Lua 参数 ===
        // 先查询活动元数据，过滤掉无预算/无日限的活动
        List<Long> activityIds = ctx.getWinningActivities().stream()
            .map(PromotionContext.ActivityCandidate::getActivityId)
            .distinct()
            .toList();

        Map<Long, PromotionActivity> activityMap = activityIds.isEmpty()
            ? Map.of()
            : activityMapper.selectBatchIds(activityIds).stream()
                .collect(Collectors.toMap(PromotionActivity::getId, a -> a));

        String today = LocalDate.now().format(DATE_FMT);
        long dailyTtl = calcDailyTtl();
        int lockTtl = props.getLockTtlSeconds();

        // 过滤后实际需要 Redis 锁的活动
        Map<Long, Long> effectiveBudgetDeductions = new LinkedHashMap<>(); // activityId -> cents
        Map<Long, Long> effectiveDailyLimits = new LinkedHashMap<>();      // activityId -> limit

        for (var ac : ctx.getWinningActivities()) {
            PromotionActivity act = activityMap.get(ac.getActivityId());
            if (act == null) continue;

            Long deduction = ctx.getBudgetDeductions().get(ac.getActivityId());
            if (deduction != null && deduction > 0 && act.getTotalBudget() != null) {
                effectiveBudgetDeductions.put(ac.getActivityId(), deduction);
            }

            Long dailyInc = ctx.getDailyIncrements().get(ac.getActivityId());
            if (dailyInc != null && dailyInc > 0 && act.getUserDailyLimit() != null) {
                effectiveDailyLimits.put(ac.getActivityId(), act.getUserDailyLimit().longValue());
            }
        }

        // 构造 KEYS
        List<String> keys = new ArrayList<>();
        for (String code : winningCodes) {
            keys.add(COUPON_LOCK_PREFIX + code);
        }
        for (Long aid : effectiveBudgetDeductions.keySet()) {
            keys.add(BUDGET_PREFIX + aid);
        }
        for (Long aid : effectiveDailyLimits.keySet()) {
            keys.add(DAILY_PREFIX + aid + ":" + req.getUserId() + ":" + today);
        }

        // 构造 ARGV
        int N = winningCodes.size();
        int B = effectiveBudgetDeductions.size();
        int D = effectiveDailyLimits.size();

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(N));
        args.add(String.valueOf(B));
        args.add(String.valueOf(D));
        args.add(String.valueOf(lockTtl));
        args.add(String.valueOf(dailyTtl));
        // 预算扣减额（分）
        for (Long cents : effectiveBudgetDeductions.values()) {
            args.add(String.valueOf(cents));
        }
        // 每日上限
        for (Long limit : effectiveDailyLimits.values()) {
            args.add(String.valueOf(limit));
        }

        // === Step 4: 执行 Lua 原子脚本 ===
        Long luaResult = redisTemplate.execute(
            lockPromotionScript, keys, args.toArray(new String[0]));

        if (luaResult == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        switch (luaResult.intValue()) {
            case 1 -> log.debug("Lua 锁定成功 orderNo={}", orderNo);
            case -1 -> throw new BusinessException(ErrorCode.COUPON_USED);
            case -2 -> throw new BusinessException(ErrorCode.PROMOTION_BUDGET_NOT_ENOUGH);
            case -3 -> throw new BusinessException(ErrorCode.PROMOTION_DAILY_LIMIT);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        // === Step 5: DB 更新（在 @Transactional 内）===
        try {
            // 5.1 券状态 0→1（条件更新）
            for (String code : winningCodes) {
                int rows = couponUserMapper.lock(code, orderNo);
                if (rows == 0) {
                    // Redis 已锁但 MySQL 条件不满足（并发冲突/券已被用）
                    compensateRedis(winningCodes, effectiveBudgetDeductions,
                        effectiveDailyLimits, req.getUserId(), today);
                    throw new BusinessException(ErrorCode.COUPON_USED);
                }
            }

            // 5.2 活动预算扣减（仅有限预算的活动）
            for (var e : effectiveBudgetDeductions.entrySet()) {
                BigDecimal amount = BigDecimal.valueOf(e.getValue(), 2); // 分转元
                int rows = activityMapper.deductBudget(e.getKey(), amount);
                if (rows == 0) {
                    compensateRedis(winningCodes, effectiveBudgetDeductions,
                        effectiveDailyLimits, req.getUserId(), today);
                    // 回滚已更新的券
                    for (String code : winningCodes) {
                        couponUserMapper.release(code);
                    }
                    throw new BusinessException(ErrorCode.PROMOTION_BUDGET_NOT_ENOUGH);
                }
            }
        } catch (BusinessException e) {
            // 已补偿或已回滚，直接抛
            throw e;
        } catch (Exception e) {
            // 未知异常：补偿 Redis + 抛错（Spring 会回滚事务）
            compensateRedis(winningCodes, effectiveBudgetDeductions,
                effectiveDailyLimits, req.getUserId(), today);
            log.error("lock DB 更新异常 orderNo={}", orderNo, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        // === Step 6: 写 promo:lock:order:{orderNo} Hash（TTL 20min）===
        try {
            writeLockHash(ctx, orderNo, winningCodes, effectiveBudgetDeductions);
        } catch (Exception e) {
            // Hash 写失败不影响 DB（已提交），但会影响 confirm/release 的精确性
            // LockTimeoutReleaseJob 会兜底扫描 DB status=1 的记录
            log.error("写锁定 Hash 失败 orderNo={}", orderNo, e);
        }

        // === Step 7: 组装响应 ===
        return buildResponseFromContext(ctx, orderNo);
    }

    // ============================================================
    // 2. confirm —— 支付后核销
    // ============================================================

    /**
     * 支付成功后核销已锁定的优惠。
     *
     * 幂等：Hash 已不存在时查 order_coupon，已有记录则直接返回。
     */
    @Transactional
    public void confirm(String orderNo) {
        String hashKey = HASH_KEY_PREFIX + orderNo;
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(hashKey);

        // 幂等检查 1：Hash 已不存在 → 可能已核销
        if (hash.isEmpty()) {
            List<OrderCoupon> snaps = orderCouponMapper.selectByOrderNo(orderNo);
            if (!snaps.isEmpty()) {
                log.debug("订单 {} 已核销（order_coupon 有记录），幂等返回", orderNo);
                return;
            }
            log.warn("订单 {} 无锁定 Hash 且无任何快照，无法核销", orderNo);
            throw new BusinessException(ErrorCode.PROMOTION_LOCK_CONFLICT);
        }

        // 读券码列表
        String couponsJson = (String) hash.get("coupons");
        List<String> couponCodes;
        try {
            couponCodes = objectMapper.readValue(couponsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("解析券码列表失败 orderNo={}", orderNo, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        // 读券面金额映射
        String couponDiscountsJson = (String) hash.get("couponDiscounts");
        Map<String, BigDecimal> couponDiscountMap;
        try {
            couponDiscountsJson = couponDiscountsJson != null ? couponDiscountsJson : "{}";
            couponDiscountMap = objectMapper.readValue(couponDiscountsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("解析券金额映射失败 orderNo={}", orderNo, e);
            couponDiscountMap = Map.of();
        }

        // DB 更新
        for (String code : couponCodes) {
            // ① 券 1→2
            int rows = couponUserMapper.confirm(code);
            if (rows == 0) {
                CouponUser cu = couponUserMapper.selectByCouponCode(code);
                if (cu != null && cu.getStatus() != null && cu.getStatus() == 2) {
                    continue; // 幂等：已核销
                }
                throw new BusinessException(ErrorCode.COUPON_USED);
            }

            // ② 写 order_coupon 快照
            CouponUser cu = couponUserMapper.selectByCouponCode(code);
            if (cu == null) continue;

            OrderCoupon oc = new OrderCoupon();
            oc.setOrderNo(orderNo);
            oc.setCouponCode(code);
            oc.setTemplateId(cu.getTemplateId());

            // 券类型快照：查模板
            // 注：模板信息理论上不会变，但为保险查一下
            // 这里简化：从 coupon_user 关联 template_id，类型从 Hash 中也可以读
            // 实际生产中应把 couponType 也写入 Hash
            oc.setCouponType("FULL_REDUCTION"); // TODO: 从模板或 Hash 中读取真实类型
            oc.setDiscountAmount(couponDiscountMap.getOrDefault(code, BigDecimal.ZERO));
            orderCouponMapper.insert(oc);

            // ③ 模板已核销数 +1
            couponTemplateMapper.incrementUsedQuantity(cu.getTemplateId());

            // ④ 删券锁
            redisTemplate.delete(COUPON_LOCK_PREFIX + code);
        }

        // ⑤ 删订单 Hash
        redisTemplate.delete(hashKey);

        // ⑥ TODO: 发布 MQ 事件 promotion.coupon.used
        log.info("订单 {} 优惠核销完成，券数={}", orderNo, couponCodes.size());
    }

    // ============================================================
    // 3. release —— 取消/超时释放
    // ============================================================

    /**
     * 释放已锁定的优惠（订单取消 / Saga 补偿 / 锁超时）。
     *
     * 幂等：Hash 不存在 → 已释放，直接返回。
     */
    @Transactional
    public void release(String orderNo) {
        String hashKey = HASH_KEY_PREFIX + orderNo;
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(hashKey);

        if (hash.isEmpty()) {
            // 检查 DB 是否还有 status=1 的券（Job 兜底路径）
            List<CouponUser> locked = couponUserMapper.selectByLockOrderNo(orderNo);
            if (locked.isEmpty()) {
                log.debug("订单 {} 无锁定记录，幂等返回", orderNo);
                return;
            }
            // Hash 已过期但 DB 还有锁 → 走 DB 释放（预算无法精确回补，记告警）
            log.warn("订单 {} Hash 已过期但 DB 仍有锁定券，仅能释放券，活动预算需人工对账", orderNo);
            for (CouponUser cu : locked) {
                couponUserMapper.release(cu.getCouponCode());
                redisTemplate.delete(COUPON_LOCK_PREFIX + cu.getCouponCode());
            }
            return;
        }

        // 读券码
        String couponsJson = (String) hash.get("coupons");
        List<String> couponCodes;
        try {
            couponCodes = objectMapper.readValue(couponsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("解析券码失败 orderNo={}", orderNo, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        // 读活动预扣额
        String activitiesJson = (String) hash.get("activities");
        Map<Long, Long> activityDeductions; // activityId -> cents
        try {
            activitiesJson = activitiesJson != null ? activitiesJson : "{}";
            activityDeductions = objectMapper.readValue(activitiesJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("解析活动预扣额失败 orderNo={}", orderNo, e);
            activityDeductions = Map.of();
        }

        // ① 券 1→0
        for (String code : couponCodes) {
            int rows = couponUserMapper.release(code);
            if (rows == 0) {
                CouponUser cu = couponUserMapper.selectByCouponCode(code);
                if (cu != null && cu.getStatus() != null && cu.getStatus() == 0) {
                    continue; // 幂等：已释放
                }
            }
            redisTemplate.delete(COUPON_LOCK_PREFIX + code);
        }

        // ② 回补活动预算
        for (var e : activityDeductions.entrySet()) {
            Long aid = e.getKey();
            long cents = e.getValue();
            BigDecimal amount = BigDecimal.valueOf(cents, 2);

            // Redis 回补
            redisTemplate.opsForValue().increment(BUDGET_PREFIX + aid, cents);
            // MySQL 回补
            activityMapper.addBackBudget(aid, amount);
        }

        // ③ 删 Hash
        redisTemplate.delete(hashKey);

        log.info("订单 {} 优惠释放完成，券数={}，活动数={}",
            orderNo, couponCodes.size(), activityDeductions.size());
    }

    // ============================================================
    // 4. refund —— 退款处理
    // ============================================================

    /**
     * 退款后的优惠处理。
     *
     * 全额退款：券标记为"已退还"(status=4)，不可再次使用；
     * 部分退款：券保持已用，仅活动预算按比例回补。
     *
     * 注：活动预算的精确回补依赖 lock 时写入的 Hash。若 Hash 已过期
     * （confirm 后删除，退款发生在数日后），则活动预算回补会丢失，
     * 需由对账 Job 兜底。生产环境建议增加 order_promotion 快照表。
     */
    @Transactional
    public void refund(RefundRequest req) {
        String orderNo = req.getOrderNo();
        BigDecimal ratio = req.getRefundRatio();

        // ① 读 order_coupon 快照
        List<OrderCoupon> usedCoupons = orderCouponMapper.selectByOrderNo(orderNo);
        if (usedCoupons.isEmpty()) {
            log.warn("订单 {} 无优惠使用记录，跳过退款处理", orderNo);
            return;
        }

        // ② 券处理
        boolean fullRefund = ratio.compareTo(BigDecimal.ONE) == 0;
        for (OrderCoupon oc : usedCoupons) {
            CouponUser cu = couponUserMapper.selectByCouponCode(oc.getCouponCode());
            if (cu == null) continue;

            if (fullRefund) {
                // 全额退款：券 → 4 已退还（不可再用，防套券）
                if (cu.getStatus() == 1 || cu.getStatus() == 2) {
                    couponUserMapper.markRefunded(oc.getCouponCode());
                }
            }
            // 部分退款：券保持已用，不动
        }

        // ③ 活动预算按比例回补（尝试读 Hash）
        String hashKey = HASH_KEY_PREFIX + orderNo;
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(hashKey);

        if (!hash.isEmpty()) {
            String activitiesJson = (String) hash.get("activities");
            try {
                Map<Long, Long> activityDeductions = objectMapper.readValue(
                    activitiesJson != null ? activitiesJson : "{}", new TypeReference<>() {});

                for (var e : activityDeductions.entrySet()) {
                    Long aid = e.getKey();
                    long originalCents = e.getValue();
                    long refundCents = BigDecimal.valueOf(originalCents)
                        .multiply(ratio)
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValue();

                    if (refundCents > 0) {
                        BigDecimal refundAmount = BigDecimal.valueOf(refundCents, 2);
                        redisTemplate.opsForValue().increment(BUDGET_PREFIX + aid, refundCents);
                        activityMapper.addBackBudget(aid, refundAmount);
                    }
                }
            } catch (JsonProcessingException e) {
                log.error("退款时解析活动预扣额失败 orderNo={}", orderNo, e);
            }
        } else {
            log.warn("订单 {} 退款时锁定 Hash 已不存在，活动预算回补可能丢失", orderNo);
        }

        // ④ TODO: 发布 MQ 事件 promotion.coupon.refunded
        log.info("订单 {} 优惠退款处理完成 ratio={} 全额={}", orderNo, ratio, fullRefund);
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 从 Redis Hash 读取历史锁定结果（lock 幂等返回用）。
     */
    private PromotionLockResponse readLockResponseFromRedis(String orderNo) {
        String hashKey = HASH_KEY_PREFIX + orderNo;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(hashKey);

        if (entries.isEmpty()) {
            throw new BusinessException(ErrorCode.PROMOTION_LOCK_CONFLICT);
        }

        try {
            String totalStr = (String) entries.get("totalDiscount");
            BigDecimal totalDiscount = new BigDecimal(totalStr);

            String itemsJson = (String) entries.get("items");
            List<PromotionLockResponse.ItemDiscount> items = objectMapper.readValue(
                itemsJson, new TypeReference<>() {});

            String splitsJson = (String) entries.get("splits");
            List<PromotionLockResponse.MerchantSplit> splits = objectMapper.readValue(
                splitsJson, new TypeReference<>() {});

            String traceJson = (String) entries.get("trace");
            List<PromotionLockResponse.PromotionTrace> trace = traceJson != null
                ? objectMapper.readValue(traceJson, new TypeReference<>() {})
                : List.of();

            return PromotionLockResponse.builder()
                .orderNo(orderNo)
                .totalDiscount(totalDiscount)
                .items(items)
                .splits(splits)
                .trace(trace)
                .build();

        } catch (JsonProcessingException | NumberFormatException e) {
            log.error("锁定结果反序列化失败 orderNo={}", orderNo, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 写 promo:lock:order:{orderNo} Hash。
     */
    private void writeLockHash(PromotionContext ctx, String orderNo,
                               List<String> winningCodes,
                               Map<Long, Long> effectiveBudgetDeductions) throws JsonProcessingException {
        String hashKey = HASH_KEY_PREFIX + orderNo;

        // 行级分摊
        List<PromotionLockResponse.ItemDiscount> itemDiscounts = new ArrayList<>();
        Map<Long, BigDecimal> splitMap = new LinkedHashMap<>();
        for (CalcItemState item : ctx.getItems()) {
            itemDiscounts.add(new PromotionLockResponse.ItemDiscount(
                item.getSkuId(), item.getAllocatedDiscount()));
            splitMap.merge(item.getMerchantId(), item.getAllocatedDiscount(), BigDecimal::add);
        }

        List<PromotionLockResponse.MerchantSplit> splits = new ArrayList<>();
        for (var e : splitMap.entrySet()) {
            splits.add(new PromotionLockResponse.MerchantSplit(e.getKey(), e.getValue()));
        }

        // trace
        List<PromotionLockResponse.PromotionTrace> traces = ctx.getTrace().stream()
            .map(h -> new PromotionLockResponse.PromotionTrace(
                h.getLayer(), h.getRefType(), h.getRefId(), h.getAmount()))
            .toList();

        // 券面金额映射（confirm 写快照用）
        Map<String, BigDecimal> couponDiscountMap = new HashMap<>();
        for (var cc : ctx.getWinningCoupons()) {
            couponDiscountMap.put(cc.getCouponCode(), cc.getCalculatedDiscount());
        }

        Map<String, String> hash = new HashMap<>();
        hash.put("orderNo", orderNo);
        hash.put("userId", String.valueOf(ctx.getUserId()));
        hash.put("totalDiscount", ctx.getTotalDiscount().toPlainString());
        hash.put("items", objectMapper.writeValueAsString(itemDiscounts));
        hash.put("splits", objectMapper.writeValueAsString(splits));
        hash.put("trace", objectMapper.writeValueAsString(traces));
        hash.put("coupons", objectMapper.writeValueAsString(winningCodes));
        hash.put("activities", objectMapper.writeValueAsString(effectiveBudgetDeductions));
        hash.put("couponDiscounts", objectMapper.writeValueAsString(couponDiscountMap));

        redisTemplate.opsForHash().putAll(hashKey, hash);
        redisTemplate.expire(hashKey, Duration.ofSeconds(props.getLockRecordTtlSeconds()));
    }

    /**
     * 从计算上下文直接组装响应（首次 lock 用）。
     */
    private PromotionLockResponse buildResponseFromContext(PromotionContext ctx, String orderNo) {
        List<PromotionLockResponse.ItemDiscount> items = new ArrayList<>();
        Map<Long, BigDecimal> splitMap = new LinkedHashMap<>();

        for (CalcItemState item : ctx.getItems()) {
            items.add(new PromotionLockResponse.ItemDiscount(
                item.getSkuId(), item.getAllocatedDiscount()));
            splitMap.merge(item.getMerchantId(), item.getAllocatedDiscount(), BigDecimal::add);
        }

        List<PromotionLockResponse.MerchantSplit> splits = new ArrayList<>();
        for (var e : splitMap.entrySet()) {
            splits.add(new PromotionLockResponse.MerchantSplit(e.getKey(), e.getValue()));
        }

        List<PromotionLockResponse.PromotionTrace> traces = ctx.getTrace().stream()
            .map(h -> new PromotionLockResponse.PromotionTrace(
                h.getLayer(), h.getRefType(), h.getRefId(), h.getAmount()))
            .toList();

        return PromotionLockResponse.builder()
            .orderNo(orderNo)
            .totalDiscount(ctx.getTotalDiscount())
            .items(items)
            .splits(splits)
            .trace(traces)
            .build();
    }

    /**
     * lock 失败时补偿 Redis：删除券锁、回补预算、回退每日计数。
     */
    private void compensateRedis(List<String> couponCodes,
                                  Map<Long, Long> budgetDeductions,
                                  Map<Long, Long> dailyLimits,
                                  Long userId, String today) {
        log.warn("补偿 Redis 锁定状态");
        for (String code : couponCodes) {
            redisTemplate.delete(COUPON_LOCK_PREFIX + code);
        }
        for (var e : budgetDeductions.entrySet()) {
            redisTemplate.opsForValue().increment(BUDGET_PREFIX + e.getKey(), e.getValue());
        }
        for (var e : dailyLimits.entrySet()) {
            redisTemplate.opsForValue().decrement(
                DAILY_PREFIX + e.getKey() + ":" + userId + ":" + today);
        }
    }

    /**
     * 计算每日计数 TTL：到当日 24:00 的秒数。
     */
    private long calcDailyTtl() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }
}
