package com.ecommerce.csservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服工单表
 *
 * <p>状态机：CREATED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED</p>
 */
@Data
@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String ticketNo;

    private Long userId;

    private String category;

    private Integer priority;

    private String status;

    private String title;

    private Long orderId;

    private Long assignedAgentId;

    private String resolutionNote;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("created_at")
    private LocalDateTime createTime;

    @TableField("updated_at")
    private LocalDateTime updateTime;
}
