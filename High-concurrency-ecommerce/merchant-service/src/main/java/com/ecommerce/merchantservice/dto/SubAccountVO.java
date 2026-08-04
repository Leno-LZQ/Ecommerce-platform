package com.ecommerce.merchantservice.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SubAccountVO {

    private Long id;
    private Long merchantId;
    private Long userId;

    /** 手机号脱敏：138****8888 */
    private String phoneMasked;

    /** 子账号角色：运营 / 客服 / 财务 */
    private String role;

    /** 0=禁用 1=正常 */
    private Integer status;

    private LocalDateTime createTime;
}
