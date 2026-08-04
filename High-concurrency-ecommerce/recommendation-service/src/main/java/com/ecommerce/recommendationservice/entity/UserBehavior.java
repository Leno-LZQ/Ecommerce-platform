package com.ecommerce.recommendationservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.recommendationservice.entity.enums.BehaviorType;
import org.apache.ibatis.type.EnumTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户行为日志表。
 */
@Data
@TableName("user_behavior")
public class UserBehavior {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long productId;

    @TableField(value = "behavior_type", typeHandler = EnumTypeHandler.class)
    private BehaviorType behaviorType;

    private String sessionId;
    private String searchKeyword;
    private Integer durationMs;

    @TableField("created_at")
    private LocalDateTime createdAt;

}
