package com.ecommerce.promotionservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单用券快照表（order_coupon）—— 下单 confirm 时固化，不可篡改
 *
 * 注意：本表只有 create_time，不继承 BaseEntity。
 */
@Data
@TableName("order_coupon")
public class OrderCoupon {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 订单号（→ orders.order_no，CASCADE）
     */
    private String orderNo;

    /**
     * 券码（→ coupon_user.coupon_code，RESTRICT）
     */
    private String couponCode;

    /**
     * 券模板 ID（快照冗余）
     */
    private Long templateId;

    /**
     * 券类型——快照固化（模板后续改类型不影响历史记录）：
     * FULL_REDUCTION / DISCOUNT / NO_THRESHOLD
     */
    private String couponType;

    /**
     * 该券实际优惠金额（元）——快照固化，
     * 取值来源：confirm 时从 promo:lock:order:{orderNo} Hash 中读取，一律不重新计算。
     * 此金额是结算与退款的计算基数。
     */
    private BigDecimal discountAmount;

    /**
     * 创建时间（MyMetaObjectHandler 自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
