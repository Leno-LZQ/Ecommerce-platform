package com.ecommerce.messageservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateConversationRequest {
    @NotNull(message = "商家id不能为空")
    private Long merchantId;

    private Long productId;
    private Long orderId;
}
