package com.ecommerce.messageservice.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MessageType {

    TEXT(1, "TEXT"),
    IMAGE(2, "IMAGE"),
    PRODUCT_CARD(3, "PRODUCT_CARD"),
    ORDER_CARD(4, "ORDER_CARD");

    private final int code;
    private final String desc;
}
