package com.ecommerce.cartservice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车 MySQL 容灾备份表实体。
 *
 * <p>主存储为 Redis，本表仅在异步同步或灾备恢复时使用。</p>
 */
@Data
@TableName("cart_item")
public class CartItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private Integer selected;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
