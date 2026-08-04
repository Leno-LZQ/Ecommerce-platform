package com.ecommerce.productservice.dto;

import lombok.Data;

@Data
public class CategoryRequest {
    private Long id;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private String icon;
    private Integer status;
}
