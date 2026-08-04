package com.ecommerce.csservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 发送工单消息请求
 */
@Data
public class SendTicketMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    private String content;

    private List<String> attachmentUrls;
}
