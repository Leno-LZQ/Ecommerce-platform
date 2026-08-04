package com.ecommerce.promotionservice.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 促销活动管理端 CRUD 请求体
 *
 * 活动与阶梯规则、适用范围一并提交（级联保存/更新），
 * 避免管理端分多次调用的复杂度。
 */
@Data
public class PromotionActivityRequest {

    /** 活动名称 */
    @NotBlank
    private String activityName;

    /** 活动类型：FULL_REDUCTION / FULL_DISCOUNT / N_M */
    @NotBlank
    private String activityType;

    /** 总预算（元），null = 不限 */
    private BigDecimal totalBudget;

    /** 每人每天参与上限，null = 不限 */
    private Integer userDailyLimit;

    /** 活动开始时间 */
    @NotNull
    private LocalDateTime startTime;

    /** 活动结束时间 */
    @NotNull
    private LocalDateTime endTime;

    /** 状态（创建时默认 0=未开始，ActivityStatusJob 推进） */
    private Integer status;

    /** 阶梯规则列表 */
    @Valid
    private List<RuleItem> rules;

    /** 适用范围列表 */
    @Valid
    private List<ScopeRequest> scopes;

    // ===== 内嵌阶梯规则 =====

    @Data
    public static class RuleItem {
        /** 最低门槛（元/件），null=不限制 */
        private BigDecimal minAmount;

        /** 最高门槛（元/件），null=上不封顶 */
        private BigDecimal maxAmount;

        /** 优惠类型：FIXED_AMOUNT / PERCENTAGE / FIXED_PRICE */
        @NotBlank
        private String discountType;

        /** 优惠值 */
        @NotNull
        private BigDecimal discountValue;

        /** 阶梯顺序 */
        private Integer sortOrder;
    }
}
