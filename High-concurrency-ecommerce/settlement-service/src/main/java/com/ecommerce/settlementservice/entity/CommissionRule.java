package com.ecommerce.settlementservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("commission_rule")
public class CommissionRule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 规则名称 */
    private String ruleName;
    /** 适用分类（NULL=全品类） */
    private Long categoryId;
    /** 适用商家（NULL=全商家） */
    private Long merchantId;
    /** 佣金率（0.0500=5%） */
    private BigDecimal rate;
    /** 最低佣金金额 */
    private BigDecimal minAmount;
    /** 佣金封顶金额（NULL=不封顶） */
    private BigDecimal maxAmount;
    /** 状态：0=停用 1=启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
