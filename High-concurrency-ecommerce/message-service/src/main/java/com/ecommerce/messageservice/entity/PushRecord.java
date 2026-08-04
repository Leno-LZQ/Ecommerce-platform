package com.ecommerce.messageservice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("push_record")
public class PushRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;                // 要推给谁
    private Long messageId;             // 推哪条消息
    private String pushChannel;         // 推送通道：IN_APP（站内信）
    private Integer pushStatus;         // 0=待推送，1=已推送，2=推送失败
    private Integer retryCount;         // 已重试次数（阶梯退避用）
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;   // 最后更新时间（判断重试间隔用）
}
