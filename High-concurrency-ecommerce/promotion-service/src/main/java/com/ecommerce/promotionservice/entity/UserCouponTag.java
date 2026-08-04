package com.ecommerce.promotionservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠标签表（user_coupon_tag）—— 精准营销打标
 *
 * 注意：本表只有 create_time，不继承 BaseEntity。
 */
@Data
@TableName("user_coupon_tag")
public class UserCouponTag {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户 ID（→ user，CASCADE 删除）
     */
    private Long userId;

    /**
     * 标签类型：NEW_USER / VIP / BIRTHDAY / INACTIVE
     */
    private String tagType;

    /**
     * 标签扩展值
     */
    private String tagValue;

    /**
     * 打标时间（MyMetaObjectHandler 自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
