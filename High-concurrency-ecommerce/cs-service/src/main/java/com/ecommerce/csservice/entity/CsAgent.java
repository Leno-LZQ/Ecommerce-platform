package com.ecommerce.csservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服人员表
 *
 * <p>注意：客服表无 deleted 字段，不继承 BaseEntity。</p>
 */
@Data
@TableName("cs_agent")
public class CsAgent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String agentName;

    private String role;

    private String status;

    private Integer maxConcurrent;

    @TableField("created_at")
    private LocalDateTime createTime;
}
