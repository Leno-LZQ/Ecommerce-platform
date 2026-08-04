package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductPageQuery {
    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    @Min(value = 1, message = "每页最少1条")
    @Max(value = 100, message = "每页最多100条")
    private Integer size = 20;

    private Long categoryId;          // 分类筛选
    private String keyword;           // 商品名称模糊搜索
    private BigDecimal minPrice;      // 最低价
    private BigDecimal maxPrice;      // 最高价
    private String brand;             // 品牌筛选
    private String promoTag;          // 营销标签筛选

    // 排序：sales_desc(销量降序), price_asc(价格升序), price_desc(价格降序), create_desc(最新)
    private String sort = "create_desc";
}
