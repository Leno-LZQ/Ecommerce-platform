package com.ecommerce.settlementservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("settlement_bill")
public class SettlementBill {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 账单号 */
    private String billNo;
    /** 商家ID（→ merchant.id, RESTRICT） */
    private Long merchantId;
    /** 结算周期开始 */
    private LocalDate periodStart;
    /** 结算周期结束 */
    private LocalDate periodEnd;
    /** 周期内订单总额 */
    private BigDecimal totalOrderAmount;
    /** 周期内优惠总额 */
    private BigDecimal totalDiscount;
    /** 应缴佣金总额 */
    private BigDecimal totalCommission;
    /** 实际结算金额（= totalOrderAmount - totalDiscount - totalCommission） */
    private BigDecimal settlementAmount;
    /** 状态：0=草稿 1=已确认 2=已打款 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
