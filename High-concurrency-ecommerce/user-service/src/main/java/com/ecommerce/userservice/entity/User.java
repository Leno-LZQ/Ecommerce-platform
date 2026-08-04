package com.ecommerce.userservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;            // 系统自动生成，不可修改

    private String password;            // BCrypt 加密

    private String phoneHash;           // HMAC-SHA256 确定性哈希（用于唯一查重）
    private String phoneEncrypted;      // AES-256-CBC 随机IV 密文（用于解密还原）

    private String email;
    private String nickname;
    private String avatar;

    private Integer status;             // 0=禁用 1=正常
    private Integer deleted;            // 逻辑删除

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
