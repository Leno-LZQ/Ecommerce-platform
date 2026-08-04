package com.ecommerce.promotionservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.promotionservice.entity.PromotionActivity;
import com.ecommerce.promotionservice.entity.PromotionRule;
import com.ecommerce.promotionservice.entity.PromotionScope;
import com.ecommerce.promotionservice.mapper.PromotionActivityMapper;
import com.ecommerce.promotionservice.mapper.PromotionRuleMapper;
import com.ecommerce.promotionservice.mapper.PromotionScopeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 促销活动与阶梯规则服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final PromotionActivityMapper activityMapper;
    private final PromotionRuleMapper ruleMapper;
    private final PromotionScopeMapper scopeMapper;

    // ==================== 查询 ====================

    public PromotionActivity getById(Long id) {
        return activityMapper.selectById(id);
    }

    public List<PromotionActivity> listAll() {
        return activityMapper.selectList(
            new LambdaQueryWrapper<PromotionActivity>()
                .orderByDesc(PromotionActivity::getCreateTime));
    }

    public List<PromotionActivity> listOngoing() {
        return activityMapper.selectOngoing();
    }

    // ==================== CRUD ====================

    @Transactional
    public Long create(PromotionActivity activity, List<PromotionRule> rules, List<PromotionScope> scopes) {
        activity.setStatus(0); // 默认未开始
        if (activity.getRemainingBudget() == null && activity.getTotalBudget() != null) {
            activity.setRemainingBudget(activity.getTotalBudget());
        }
        activityMapper.insert(activity);

        // 保存规则
        if (rules != null) {
            for (PromotionRule rule : rules) {
                rule.setActivityId(activity.getId());
                ruleMapper.insert(rule);
            }
        }

        // 保存适用范围
        if (scopes != null) {
            for (PromotionScope scope : scopes) {
                scope.setTargetType("PROMOTION");
                scope.setTargetId(activity.getId());
                scopeMapper.insert(scope);
            }
        }

        return activity.getId();
    }

    @Transactional
    public void update(Long id, PromotionActivity activity, List<PromotionRule> rules, List<PromotionScope> scopes) {
        PromotionActivity existing = activityMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROMOTION_ENDED);
        }

        activity.setId(id);
        activityMapper.updateById(activity);

        // 规则：先删后插（简单策略）
        if (rules != null) {
            ruleMapper.delete(
                new LambdaQueryWrapper<PromotionRule>()
                    .eq(PromotionRule::getActivityId, id));
            for (PromotionRule rule : rules) {
                rule.setActivityId(id);
                ruleMapper.insert(rule);
            }
        }

        // 适用范围：先删后插
        if (scopes != null) {
            scopeMapper.delete(
                new LambdaQueryWrapper<PromotionScope>()
                    .eq(PromotionScope::getTargetType, "PROMOTION")
                    .eq(PromotionScope::getTargetId, id));
            for (PromotionScope scope : scopes) {
                scope.setTargetType("PROMOTION");
                scope.setTargetId(id);
                scopeMapper.insert(scope);
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        PromotionActivity existing = activityMapper.selectById(id);
        if (existing == null) return;
        // 进行中活动不允许删除
        if (existing.getStatus() != null && existing.getStatus() == 1) {
            throw new BusinessException(ErrorCode.PROMOTION_ENDED);
        }
        ruleMapper.delete(
            new LambdaQueryWrapper<PromotionRule>()
                .eq(PromotionRule::getActivityId, id));
        scopeMapper.delete(
            new LambdaQueryWrapper<PromotionScope>()
                .eq(PromotionScope::getTargetType, "PROMOTION")
                .eq(PromotionScope::getTargetId, id));
        activityMapper.deleteById(id);
    }
}
