package com.ecommerce.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryAggregation {
    private Long id;
    private String name;
    private Long count;
}
