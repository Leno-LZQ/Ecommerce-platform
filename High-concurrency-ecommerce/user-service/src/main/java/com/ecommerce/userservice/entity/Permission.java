package com.ecommerce.userservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("permission")
public class Permission {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;          // 权限码，如 user:list
    private String name;          // 权限名称
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
