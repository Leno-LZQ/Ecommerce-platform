package com.ecommerce.recommendationservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 离线计算推荐结果物化表。
 */
@Data
@TableName("reco_result")
public class RecoResult {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long productId;
    private String strategy;

    private BigDecimal score;
    private Integer position;

    @TableField("generated_at")
    private LocalDateTime generatedAt;

}
