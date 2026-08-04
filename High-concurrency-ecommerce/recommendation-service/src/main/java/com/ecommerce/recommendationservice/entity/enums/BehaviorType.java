package com.ecommerce.recommendationservice.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户行为类型。
 */
@Getter
@AllArgsConstructor
public enum BehaviorType {

    VIEW(1, "浏览", 1),
    SEARCH(2, "搜索", 3),
    ADD_CART(3, "加购", 5),
    PURCHASE(4, "购买", 10),
    COLLECT(5, "收藏", 4),
    SHARE(6, "分享", 3);

    private final int code;
    private final String desc;
    private final int weight;

    public static BehaviorType of(String name) {
        for (BehaviorType value : values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }
}
