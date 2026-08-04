package com.ecommerce.recommendationservice.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 推荐策略码。
 */
@Getter
@AllArgsConstructor
public enum RecoStrategyType {

    CF_USER("CF_USER", "协同过滤-用户"),
    CF_ITEM("CF_ITEM", "协同过滤-物品"),
    HOT("HOT", "热门排行"),
    NEW_USER("NEW_USER", "新人推荐"),
    ASSOC_RULE("ASSOC_RULE", "关联规则");

    private final String code;
    private final String name;

    public static RecoStrategyType of(String code) {
        for (RecoStrategyType value : values()) {
            if (value.getCode().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
