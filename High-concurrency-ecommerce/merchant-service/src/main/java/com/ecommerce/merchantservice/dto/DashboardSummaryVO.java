package com.ecommerce.merchantservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryVO {

    /** 待审核商品数（tab 红点） */
    private Long pendingProducts;

    /** 待确认账单数（tab 红点） */
    private Long pendingBills;

    /** 新支付订单数（tab 红点） */
    private Long paidOrders;
}
