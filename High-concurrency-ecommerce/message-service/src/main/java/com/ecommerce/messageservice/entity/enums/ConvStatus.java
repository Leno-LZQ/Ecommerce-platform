package com.ecommerce.messageservice.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ConvStatus {
    ACTIVE(1, "进行中"),
    ARCHIVED(2, "已归档");

    private final int code;
    private final String desc;
}
