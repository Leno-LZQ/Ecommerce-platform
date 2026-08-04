package com.ecommerce.csservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户创建工单请求
 */
@Data
public class CreateTicketRequest {

    @NotBlank(message = "工单分类不能为空")
    private String category;

    @NotNull(message = "优先级不能为空")
    private Integer priority;

    @NotBlank(message = "工单标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200字符")
    private String title;

    private Long orderId;

    @NotBlank(message = "问题描述不能为空")
    private String content;
}
