package com.ecommerce.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchItem {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer salesCount;
    private Double rating;
    private String mainImage;
}
