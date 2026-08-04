package com.ecommerce.csservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单操作审计日志表
 *
 * <p>所有状态变更（分配/转接/解决/关闭/重开）都写入此表，不可删除。</p>
 */
@Data
@TableName("ticket_audit_log")
public class TicketAuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long ticketId;

    private Long operatorId;

    private String action;

    private String detail;

    @TableField("created_at")
    private LocalDateTime createTime;
}
