package com.ecommerce.productservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class CategoryResponse {
    private Long id;
    private String name;
    private Long parentId;
    private Integer status;
    private Integer level;         // 1/2/3
    private String icon;
    private Integer sortOrder;
    private List<CategoryResponse> children;  // 递归子分类
}
