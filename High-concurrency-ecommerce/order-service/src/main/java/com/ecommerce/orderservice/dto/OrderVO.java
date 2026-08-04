package com.ecommerce.orderservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer status;
    private Integer isMultiMerchant;
    private Integer payType;
    private LocalDateTime payTime;
    private String consignee;
    private String phone;
    private String address;
    private String remark;
    private String cancelReason;
}
