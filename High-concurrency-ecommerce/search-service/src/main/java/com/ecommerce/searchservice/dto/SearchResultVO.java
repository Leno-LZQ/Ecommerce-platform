package com.ecommerce.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultVO {
    private Long total;
    private Integer page;
    private Integer size;
    private List<SearchItem> items;
    private Aggregations aggregations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Aggregations {
        private List<CategoryAggregation> categories;
        private List<BrandAggregation> brands;
        private List<PriceRangeAggregation> priceRanges;
    }
}
