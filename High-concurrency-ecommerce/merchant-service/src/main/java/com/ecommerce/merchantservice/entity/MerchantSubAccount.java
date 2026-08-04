package com.ecommerce.merchantservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "merchant_sub_account", autoResultMap = true)
public class MerchantSubAccount {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long merchantId;

    /** 关联的用户ID（一个用户一个子账号） */
    private Long userId;

    /** 子账号角色：运营 / 客服 / 财务 */
    private String role;

    /** 0=禁用 1=正常 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
