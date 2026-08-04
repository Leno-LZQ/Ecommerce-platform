package com.ecommerce.merchantservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantEvent implements Serializable {

    private Long merchantId;
    private Long userId;
    private String shopName;

    /** 0=待审核 1=正常 2=拒绝 3=冻结 4=注销 */
    private Integer status;

    /** 审核备注（拒绝时附带原因，冻结/注销时可选） */
    private String remark;

    /** 事件时间戳 */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}

