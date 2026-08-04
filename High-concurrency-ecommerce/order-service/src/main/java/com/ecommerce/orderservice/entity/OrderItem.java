package com.ecommerce.orderservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "order_item", autoResultMap = true)
public class OrderItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;
    private Long merchantId;
    private String subOrderNo;
    private Long productId;
    private Long skuId;
    private String productName;
    private String productImage;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> skuAttrs;

    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
