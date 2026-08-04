package com.ecommerce.merchantservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "merchant", autoResultMap = true)
public class Merchant {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String shopName;
    private String shopLogo;
    private String shopDesc;
    private String contactName;
    private String contactPhone;

    /** 0=待审核 1=正常 2=拒绝 3=冻结 4=注销 */
    private Integer status;

    private String auditRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
