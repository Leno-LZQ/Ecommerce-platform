package com.ecommerce.promotionservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.promotionservice.entity.CouponTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 优惠券模板 Mapper
 *
 * 原子库存操作说明：
 * issued_quantity 的更新不走 MyBatis-Plus updateById（存在竞态），
 * 必须用自定义 SQL 的 WHERE 条件充当乐观锁。
 */
@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {

    /**
     * 原子增加已领取数量（领券双闸门的 DB 侧兜底）
     * 影响行数=0 → 库存耗尽或并发冲突，调用方应回补 Redis 库存并抛 COUPON_STOCK_OUT
     */
    @Update("UPDATE coupon_template SET issued_quantity = issued_quantity + 1 " +
            "WHERE id = #{id} AND issued_quantity < total_quantity")
    int incrementIssuedQuantity(@Param("id") Long id);

    /**
     * 原子增加已核销数量（confirm 后调用）
     */
    @Update("UPDATE coupon_template SET used_quantity = used_quantity + 1 " +
            "WHERE id = #{id}")
    int incrementUsedQuantity(@Param("id") Long id);
}
