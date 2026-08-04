package com.ecommerce.csservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户确认解决 / 不满意重新打开
 */
@Data
public class ResolveConfirmRequest {

    @NotNull(message = "是否满意不能为空")
    private Boolean satisfied;
}
