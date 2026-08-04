package com.ecommerce.recommendationservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 推荐策略配置表。
 */
@Data
@TableName("reco_strategy")
public class RecoStrategy {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String strategyCode;
    private String strategyName;
    private BigDecimal weight;
    private Integer enabled;

    @TableField("config_json")
    private String configJson;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

}
