package com.ecommerce.recommendationservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品相似度矩阵表（Item-CF 离线计算）。
 */
@Data
@TableName("product_similarity")
public class ProductSimilarity {

    @TableId(value = "product_id_a", type = IdType.INPUT)
    private Long productIdA;

    private Long productIdB;

    private BigDecimal similarity;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

}
