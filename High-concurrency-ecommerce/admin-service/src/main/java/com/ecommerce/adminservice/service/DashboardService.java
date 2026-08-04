package com.ecommerce.adminservice.service;

import com.ecommerce.adminservice.dto.DashboardOverview;
import com.ecommerce.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@Slf4j
public class DashboardService {

    private final MerchantClient merchantClient;
    private final ProductClient  productClient;
    private final OrderClient    orderClient;
    private final PaymentClient  paymentClient;   // 需要加 getTodayRevenue
    private final UserClient userClient;       // 需要加 getTodayNewUsers
    private final ExecutorService executor;        // 自定义线程池

    public DashboardService(MerchantClient merchantClient, ProductClient productClient, OrderClient orderClient, PaymentClient paymentClient, UserClient userClient, ExecutorService executor) {
        this.merchantClient = merchantClient;
        this.productClient = productClient;
        this.orderClient = orderClient;
        this.paymentClient = paymentClient;
        this.userClient = userClient;
        this.executor = executor;
    }


    public DashboardOverview getOverview() {
        // 5 路并行，各自 3s 超时，失败 fallback 为默认值
        CompletableFuture<Long> merchantF = supplyAsync(
            () -> merchantClient.getTotalCount().getData(), 0L);
        CompletableFuture<Long> productF = supplyAsync(
            () -> productClient.countByMerchantAndAuditStatus(null, 1).getData(), 0L);
        CompletableFuture<Long> orderF = supplyAsync(
            () -> orderClient.countByStatus(null).getData(), 0L);
        CompletableFuture<BigDecimal> revenueF = supplyAsync(
            () -> paymentClient.getTodayRevenue().getData(), BigDecimal.ZERO);
        CompletableFuture<Long> newUserF = supplyAsync(
            () -> userClient.getTodayNewUsers().getData(), 0L);

        CompletableFuture.allOf(merchantF, productF, orderF, revenueF, newUserF).join();

        return DashboardOverview.builder()
            .merchantCount(merchantF.getNow(0L))
            .productCount(productF.getNow(0L))
            .todayOrders(orderF.getNow(0L))
            .todayRevenue(revenueF.getNow(BigDecimal.ZERO))
            .todayNewUsers(newUserF.getNow(0L))
            .pendingMerchantAudit(safeGet(
                () -> merchantClient.getPendingAuditCount().getData(), 0L))
            .pendingProductAudit(safeGet(
                () -> productClient.countByMerchantAndAuditStatus(null, 0).getData(), 0L))
            .openTickets(0L)           // cs-service 未实现
            .refundOrders(safeGet(
                () -> orderClient.countByStatus(5).getData(), 0L))
            .build();
    }

    /** 并行调用的包装：3s 超时 + 异常兜底 */
    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, T fallback) {
        return CompletableFuture.supplyAsync(supplier, executor)
            .orTimeout(3, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                log.warn("Dashboard 数据源调用失败", ex);
                return fallback;
            });
    }

    /** 同步调用包装：异常兜底 */
    private <T> T safeGet(Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("Dashboard 同步查询失败", e);
            return fallback;
        }
    }



}
