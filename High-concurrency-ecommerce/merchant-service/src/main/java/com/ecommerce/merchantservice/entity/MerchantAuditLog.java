package com.ecommerce.merchantservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "merchant_audit_log", autoResultMap = true)
public class MerchantAuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long merchantId;

    /** 操作人ID（管理员） */
    private Long operatorId;

    /** 变更前状态：0=待审 1=正常 2=拒绝 3=冻结 4=注销 */
    private Integer fromStatus;

    /** 变更后状态 */
    private Integer toStatus;

    /** 审核备注/原因 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
