package com.ecommerce.csservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 转接工单请求
 */
@Data
public class TransferRequest {

    @NotNull(message = "目标客服ID不能为空")
    private Long targetAgentId;

    private String reason;
}
