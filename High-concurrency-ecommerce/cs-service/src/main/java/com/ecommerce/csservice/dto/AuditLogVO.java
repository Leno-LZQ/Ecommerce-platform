package com.ecommerce.csservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志 VO
 */
@Data
public class AuditLogVO {

    private Long id;

    private Long operatorId;

    private String action;

    private String detail;

    private LocalDateTime createTime;
}
