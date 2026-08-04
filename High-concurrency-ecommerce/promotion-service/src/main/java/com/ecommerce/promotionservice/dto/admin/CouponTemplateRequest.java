package com.ecommerce.promotionservice.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券模板管理端 CRUD 请求体
 *
 * create 时除 id 外均需传；update 时只传变更字段即可（service 层做 diff）。
 */
@Data
public class CouponTemplateRequest {

    /** 券名称 */
    @NotBlank(message = "券名称不能为空")
    private String templateName;

    /** 券类型：FULL_REDUCTION / DISCOUNT / NO_THRESHOLD */
    @NotBlank(message = "券类型不能为空")
    private String couponType;

    /** 优惠值 */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal discountValue;

    /** 使用门槛（无门槛券传 0） */
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal thresholdAmount;

    /** 发行总量 */
    @NotNull
    @Min(1)
    private Integer totalQuantity;

    /** 每人限领（默认 1） */
    @Min(1)
    private Integer perUserLimit;

    /** 有效天数 */
    @NotNull
    @Min(1)
    private Integer validDays;

    /** 领取开始时间 */
    @NotNull
    private LocalDateTime startTime;

    /** 领取结束时间 */
    @NotNull
    private LocalDateTime endTime;

    /** 是否可叠加 */
    private Integer stackable;

    /** 状态：0=停用 1=启用（创建时默认 1） */
    private Integer status;
}
