package com.ecommerce.adminservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverview {
    private Long merchantCount;          // 商家总数
    private Long productCount;           // 在售商品总数
    private Long todayOrders;            // 今日订单数
    private BigDecimal todayRevenue;     // 今日交易额（元）
    private Long todayNewUsers;          // 今日新增用户
    private Long pendingMerchantAudit;   // 待审核商家数
    private Long pendingProductAudit;    // 待审核商品数
    private Long openTickets;            // 未关闭工单数（cs-service 未实现，先 fallback 为 0）
    private Long refundOrders;           // 退款中订单数
}

