package com.ecommerce.productservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value="review",autoResultMap = true)
public class Review {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long productId;
    private Long skuId;
    private Long userId;
    private Integer score;
    private String orderNo;
    private String content;
    private LocalDateTime createTime;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;// 晒图 URL 列表
    private Integer status;

}
