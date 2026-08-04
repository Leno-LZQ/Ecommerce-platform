package com.ecommerce.merchantservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "merchant_qualification", autoResultMap = true)
public class MerchantQualification {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long merchantId;

    /** 资质类型：BUSINESS_LICENSE / ID_CARD / FOOD_PERMIT */
    private String qualType;

    /** 资质文件 OSS URL */
    private String qualFileUrl;

    /** 审核状态：0=待审 1=通过 2=拒绝 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
