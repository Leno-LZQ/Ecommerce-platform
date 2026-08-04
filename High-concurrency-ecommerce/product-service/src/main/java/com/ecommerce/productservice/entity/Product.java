package com.ecommerce.productservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import net.bytebuddy.asm.Advice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "product", autoResultMap = true)
public class Product {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private String description;
    private Long categoryId;
    private Long merchantId;
    private String brand;
    private String mainImage;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    private Integer status;
    private Integer auditStatus;
    private String auditRemark;
    private Long sales;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String promoTag;
    private LocalDateTime promoEndTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
