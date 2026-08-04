package com.ecommerce.searchservice.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SearchRequest {

    private String keyWord;

    private Long categoryId;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    /** 排序: default(综合) / sales(销量) / price_asc(价格升) / price_desc(价格降) / rating(评分) */
    private String sort = "default";

    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    private Integer size = 20;
}
