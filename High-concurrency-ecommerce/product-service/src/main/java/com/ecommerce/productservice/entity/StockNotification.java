package com.ecommerce.productservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import net.bytebuddy.asm.Advice;

import java.time.LocalDateTime;

@Data
@TableName("stock_notification")
public class StockNotification {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long skuId;
    private Long userId;
    private Integer notified;
    private LocalDateTime createTime;
}
