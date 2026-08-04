package com.ecommerce.dto.order;

import lombok.Data;

@Data
public class OrderDTO {
    private String orderNo;
    private Long userId;
    private Integer status;     // 3=已完成
}
