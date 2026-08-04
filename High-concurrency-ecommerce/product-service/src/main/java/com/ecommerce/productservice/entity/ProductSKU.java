package com.ecommerce.productservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "product_sku" , autoResultMap = true)
public class ProductSKU {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long productId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> attrs; //规格属性
    private BigDecimal price; //价格
    private Integer stock;

    @Version
    private Integer version; //乐观锁版本号

    private LocalDateTime createTime; //创建时间
    private LocalDateTime updateTime; //更新时间

    @TableField(exist = false)
    private Integer realStock;           // inventory-service 实时库存（不持久化）

}
