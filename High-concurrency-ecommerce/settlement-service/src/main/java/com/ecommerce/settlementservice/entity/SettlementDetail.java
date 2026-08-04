package com.ecommerce.settlementservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("settlement_detail")
public class SettlementDetail {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属账单ID（→ settlement_bill.id, CASCADE） */
    private Long billId;
    /** 子订单号 */
    private String subOrderNo;
    /** 订单金额 */
    private BigDecimal orderAmount;
    /** 优惠金额 */
    private BigDecimal discountAmount;
    /** 适用佣金规则ID */
    private Long ruleId;
    /** 佣金率快照（结算时固化） */
    private BigDecimal rateSnapshot;
    /** 该笔佣金 */
    private BigDecimal commission;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
