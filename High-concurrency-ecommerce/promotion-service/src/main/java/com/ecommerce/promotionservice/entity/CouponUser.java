package com.ecommerce.promotionservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户领券记录表（coupon_user）—— 券状态机核心
 *
 * 券码对外交互一律用 coupon_code（雪花转串，非递增防枚举），不暴露自增语义。
 * 注意：本表只有 create_time（无 update_time / deleted），不继承 BaseEntity。
 */
@Data
@TableName("coupon_user")
public class CouponUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 券码（雪花转 32 位字符串，唯一键，防枚举）
     */
    private String couponCode;

    /**
     * 券模板 ID（→ coupon_template，RESTRICT）
     */
    private Long templateId;

    /**
     * 用户 ID（→ user，CASCADE）
     */
    private Long userId;

    /**
     * 状态：0=未使用 1=已锁定（下单中） 2=已使用 3=已过期 4=已退还
     *
     * 所有流转用条件更新保证幂等：
     * UPDATE ... SET status=?, ... WHERE coupon_code=? AND status=<期望前态>
     */
    private Integer status;

    /**
     * 锁定它的订单号（释放/核销时的定位键 + 幂等键）
     */
    private String lockOrderNo;

    /**
     * 过期时间（= 领取时刻 + coupon_template.valid_days）
     */
    private LocalDateTime expireTime;

    /**
     * 核销时间
     */
    private LocalDateTime useTime;

    /**
     * 领取时间（MyMetaObjectHandler 自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
