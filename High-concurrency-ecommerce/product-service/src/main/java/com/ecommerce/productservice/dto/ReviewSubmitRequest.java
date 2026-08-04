package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class ReviewSubmitRequest {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    @NotNull(message = "评分不能为空")
    @Min(1) @Max(5)
    private Integer score;

    @Size(min = 5, max = 1000, message = "评价内容5-1000字符")
    private String content;

    @Size(max = 9, message = "晒图最多9张")
    private List<String> images;
}
