package com.ecommerce.promotionservice.engine;

import com.ecommerce.promotionservice.entity.SeckillActivity;
import com.ecommerce.promotionservice.mapper.SeckillActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;


/**
 * Layer 1 — 秒杀价替换。
 * 命中秒杀的 SKU 以 seckillPrice 替换原价，
 * 并标记 excludeFromFurtherPromos = true（退出后续 L2~L5）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class SeckillStep implements CalcStep{
    private final SeckillActivityMapper seckillMapper;

    @Override
    public void apply(PromotionContext ctx) {

        for(CalcItemState item : ctx.getItems()){
            SeckillActivity seckill = seckillMapper.selectOngoingBySkuId(item.getSkuId());
            if(seckill == null)
                continue;

            if(item.getQuantity() > seckill.getPerUserLimit()){
                log.debug("用户购买数量 {} 超过秒杀限购 {}，skuId={}",
                    item.getQuantity(), seckill.getPerUserLimit(), item.getSkuId());
                continue;   // 超限购，不享受秒杀价，继续走后续层
            }

            // 计算优惠
            BigDecimal discountPerUnit = item.getOriginPrice().subtract(seckill.getSeckillPrice());
            BigDecimal totalDiscount = discountPerUnit.multiply(BigDecimal.valueOf(item.getQuantity()));

            // 替换价格
            item.applySeckill(seckill.getId(),seckill.getSeckillPrice());

            // 标记退出后续所有层
            item.excludeFromFurtherPromos();

            // 记录命中
            CalcItemState.HitEntry hit = new CalcItemState.HitEntry(
                "L1", "SECKILL", seckill.getId(), totalDiscount);
            item.getHits().add(hit);
            ctx.getTrace().add(hit);

            log.debug("秒杀命中 skuId={}, seckillId={}, 原价={}, 秒杀价={}, 优惠={}",
                item.getSkuId(), seckill.getId(),
                item.getOriginPrice(), seckill.getSeckillPrice(), totalDiscount);
        }

    }
}
