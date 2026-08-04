package com.ecommerce.promotionservice.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动管理端 CRUD 请求体
 */
@Data
public class SeckillActivityRequest {

    /** 活动名称 */
    @NotBlank
    private String activityName;

    /** 商品 ID */
    @NotNull
    private Long productId;

    /** SKU ID */
    @NotNull
    private Long skuId;

    /** 秒杀价 */
    @NotNull
    private BigDecimal seckillPrice;

    /** 秒杀库存 */
    @NotNull
    @Min(1)
    private Integer seckillStock;

    /** 每人限购 */
    @Min(1)
    private Integer perUserLimit;

    /** 开始时间 */
    @NotNull
    private LocalDateTime startTime;

    /** 结束时间 */
    @NotNull
    private LocalDateTime endTime;

    /** 状态（创建默认 0） */
    private Integer status;

    /** 适用范围（可选，空=全场适用） */
    private List<ScopeRequest> scopes;
}
