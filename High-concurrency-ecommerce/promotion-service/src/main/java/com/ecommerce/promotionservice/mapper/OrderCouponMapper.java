package com.ecommerce.promotionservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.promotionservice.entity.OrderCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单用券快照 Mapper
 *
 * 所有写入在 confirm 时一次性完成（INSERT），后续只读。
 * 数据来源：promo:lock:order:{orderNo} Hash 中固化的分摊结果，严禁重新计算。
 */
@Mapper
public interface OrderCouponMapper extends BaseMapper<OrderCoupon> {

    /**
     * 按订单号查用券快照列表（售后/退款时还原优惠明细）
     */
    @Select("SELECT * FROM order_coupon WHERE order_no = #{orderNo}")
    List<OrderCoupon> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 按券码查使用记录
     */
    @Select("SELECT * FROM order_coupon WHERE coupon_code = #{couponCode}")
    OrderCoupon selectByCouponCode(@Param("couponCode") String couponCode);
}
