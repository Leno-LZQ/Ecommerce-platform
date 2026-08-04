package com.ecommerce.productservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductCreateRequest {
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200, message = "商品名称最长200字符")
    private String name;

    private String description;        // 富文本描述

    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    @NotBlank(message = "品牌不能为空")
    @Size(max = 100, message = "品牌最长100字符")
    private String brand;

    @NotBlank(message = "主图不能为空")
    private String mainImage;

    @Size(max = 10, message = "图片最多10张")
    private List<String> images;       // 含主图在内的所有图片

    @NotEmpty(message = "至少需要一个SKU")
    @Size(max = 50, message = "SKU最多50个")
    @Valid
    private List<SkuRequest> skus;

    private String promoTag;           // 可选营销标签
    private LocalDateTime promoEndTime;
}

