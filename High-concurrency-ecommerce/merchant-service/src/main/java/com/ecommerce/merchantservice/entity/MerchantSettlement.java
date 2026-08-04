package com.ecommerce.merchantservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "merchant_settlement", autoResultMap = true)
public class MerchantSettlement {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long merchantId;

    /** 开户银行 */
    private String bankName;

    /** 银行账号（仅存后4位明文） */
    private String bankAccount;

    /** 开户人姓名 */
    private String accountHolder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
