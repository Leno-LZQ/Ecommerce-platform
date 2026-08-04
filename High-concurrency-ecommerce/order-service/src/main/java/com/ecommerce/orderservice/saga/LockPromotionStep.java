package com.ecommerce.orderservice.saga;

import com.ecommerce.client.PromotionClient;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.dto.promotion.PromoCalcItem;
import com.ecommerce.dto.promotion.PromotionLockRequest;
import com.ecommerce.dto.promotion.PromotionLockResponse;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.orderservice.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LockPromotionStep implements SagaStep {

    private final PromotionClient promotionClient;

    @Override
    public void execute(SagaContext context) {
        // 1. 始终执行锁券：活动优惠（满减/满折）不依赖券码，券码为空时引擎只匹配活动
        // 2. OrderItem → PromoCalcItem（补 categoryId）
        List<PromoCalcItem> calcItems = context.getItems().stream()
            .map(item -> buildCalcItem(item, context))
            .toList();

        // 3. 组装请求
        PromotionLockRequest req = new PromotionLockRequest();
        req.setOrderNo(context.getOrderNo());
        req.setUserId(context.getUserId());
        req.setItems(calcItems);
        req.setCouponCodes(context.getCouponCodes());

        // 4. 远程调用
        PromotionLockResponse resp = promotionClient.lock(req).getData();
        if (resp == null || resp.getTotalDiscount() == null) {
            throw new BusinessException(ErrorCode.PROMOTION_LOCK_FAILED);
        }

        // 5. 优惠金额存回 context（后续计算 payAmount = totalAmount - discountAmount）
        context.setDiscountAmount(resp.getTotalDiscount());
        context.setLockResponse(resp);
        // 6. 同步实付金额：建支付单（CreatePaymentStep）使用优惠后金额
        if (resp.getTotalDiscount() != null) {
            context.setPayAmount(context.getPayAmount().subtract(resp.getTotalDiscount()));
        }
        log.info("锁券成功: orderNo={}, discount={}", context.getOrderNo(), resp.getTotalDiscount());
    }

    @Override
    public void compensate(SagaContext context) {
        log.info("补偿释放优惠: orderNo={}", context.getOrderNo());
        promotionClient.release(context.getOrderNo());
    }

    // ══════════════════════════════════════════
    //  辅助方法
    // ══════════════════════════════════════════

    private PromoCalcItem buildCalcItem(OrderItem item, SagaContext context) {
        PromoCalcItem ci = new PromoCalcItem();
        ci.setSkuId(item.getSkuId());
        ci.setProductId(item.getProductId());
        ci.setMerchantId(item.getMerchantId());
        ci.setPrice(item.getPrice());
        ci.setQuantity(item.getQuantity());
        // categoryId 由编排器预先查 product 表塞进 context
        ci.setCategoryId(context.getProductCategoryMap().get(item.getProductId()));
        return ci;
    }
}
