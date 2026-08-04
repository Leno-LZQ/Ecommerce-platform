package com.ecommerce.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceRangeAggregation {
    private String key;      // 如 "0-500"
    private Long count;
}
