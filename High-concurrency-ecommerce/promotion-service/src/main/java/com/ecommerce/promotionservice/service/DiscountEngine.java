package com.ecommerce.promotionservice.service;

import com.ecommerce.promotionservice.engine.CalcMode;
import com.ecommerce.promotionservice.engine.CalcStep;
import com.ecommerce.promotionservice.engine.CalcItemState;
import com.ecommerce.promotionservice.engine.PromotionContext;
import com.ecommerce.dto.promotion.PromoCalcItem;
import com.ecommerce.dto.promotion.PromotionLockResponse;
import com.ecommerce.dto.promotion.PromotionPreviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountEngine {

    /**
     * Spring 自动注入所有 CalcStep 实现，按 @Order 从小到大排序。
     * 新增优惠类型只需添加 @Component + @Order 的 Step 即可，符合开闭原则。
     */

    @Autowired
    private List<CalcStep> steps;


    // ==================== 核心入口 ====================

    /**
     * 执行五层优惠管线，PREVIEW 与 LOCK 同一入口，保证预览金额 = 锁定金额。
     *
     * @param userId       用户 ID
     * @param couponCodes  券码列表（可为空）
     * @param items        订单/购物车行项
     * @param mode         PREVIEW 或 LOCK
     * @return 计算完成的上下文（含 totalDiscount / 行分摊 / trace / LOCK 专用的 budgetDeductions）
     */
    public PromotionContext calculate (Long userId,List<String> couponCodes,List<PromoCalcItem> items,CalcMode mode){

        PromotionContext ctx = new PromotionContext(userId,couponCodes,items,mode);
        for(CalcStep step : steps){
            step.apply(ctx);
        }
        log.info("DiscountEngine 管线启动, mode={}, steps={}",
            mode, steps.stream().map(Object::getClass).toList());
        return ctx;
    }



    // ==================== 结果转换：PREVIEW ====================

    /**
     * 将管线结果转换为购物车预览 VO 列表。
     * 按优惠类型聚合：秒杀 / 活动 / 券 → 每类一条。
     */
    public List<PromotionPreviewVO> preview(PromotionContext ctx){
        List<PromotionPreviewVO> list = new ArrayList<>();

        //秒杀
        for(CalcItemState item : ctx.getItems()){
            if(item.isSeckillHit()){
                list.add(new PromotionPreviewVO(
                    "SECKILL","秒杀价",
                    item.getOriginPrice().subtract(item.getCurrentPrice())
                        .multiply(BigDecimal.valueOf(item.getQuantity()))
                ));
            }
        }

        //活动

        for(var ac : ctx.getWinningActivities()){
            list.add(new PromotionPreviewVO("ACTIVITY",
                "满减活动", ac.getDiscountAmount()));
        }

        //券

        for (var cc : ctx.getWinningCoupons()) {
            list.add(new PromotionPreviewVO("COUPON",
                cc.getCouponCode(), cc.getCalculatedDiscount()));
        }

        return list;
    }



    // ==================== 结果转换：LOCK ====================

    /**
     * 将管线结果转换为 lock 响应，order-service 据此写订单。
     */
    public PromotionLockResponse toLockResponse(PromotionContext ctx, String orderNo) {
        // ① 行级分摊
        List<PromotionLockResponse.ItemDiscount> itemDiscounts = new ArrayList<>();
        for (CalcItemState item : ctx.getItems()) {
            itemDiscounts.add(new PromotionLockResponse.ItemDiscount(
                item.getSkuId(), item.getAllocatedDiscount()));
        }

        // ② 按商家汇总
        Map<Long, BigDecimal> splitMap = new HashMap<>();
        for (CalcItemState item : ctx.getItems()) {
            splitMap.merge(item.getMerchantId(), item.getAllocatedDiscount(), BigDecimal::add);
        }
        List<PromotionLockResponse.MerchantSplit> splits = new ArrayList<>();
        for (var e : splitMap.entrySet()) {
            splits.add(new PromotionLockResponse.MerchantSplit(e.getKey(), e.getValue()));
        }

        // ③ 组装
        return new PromotionLockResponse(orderNo, ctx.getTotalDiscount(), itemDiscounts, splits);
    }

}
