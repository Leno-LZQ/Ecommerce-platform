package com.ecommerce.orderservice.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Saga 编排器：按顺序执行步骤，任意步骤失败则倒序补偿
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final ReserveInventoryStep reserveInventoryStep;
    private final LockPromotionStep lockPromotionStep;
    private final CreatePaymentStep createPaymentStep;

    /** 步骤执行顺序 */
    public SagaContext execute(SagaContext context) {
        List<SagaStep> steps = List.of(reserveInventoryStep, lockPromotionStep, createPaymentStep);

        int completed = 0;
        try {
            for (SagaStep step : steps) {
                step.execute(context);
                completed++;
            }
            return context;
        } catch (Exception e) {
            log.error("Saga 步骤 {} 执行失败，开始倒序补偿: orderNo={}",
                    completed, context.getOrderNo(), e);
            // 倒序补偿已完成步骤
            for (int i = completed - 1; i >= 0; i--) {
                try {
                    steps.get(i).compensate(context);
                } catch (Exception compEx) {
                    log.error("补偿步骤 {} 失败（由 order_message 重试兜底）: orderNo={}",
                            i, context.getOrderNo(), compEx);
                }
            }
            throw e;
        }
    }
}
