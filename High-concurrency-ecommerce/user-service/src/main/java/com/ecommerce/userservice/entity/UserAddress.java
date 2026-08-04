package com.ecommerce.userservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_address")
public class UserAddress {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    @TableField("consignee")
    private String receiverName;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;

    private Integer isDefault;    // 0=否 1=是

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
