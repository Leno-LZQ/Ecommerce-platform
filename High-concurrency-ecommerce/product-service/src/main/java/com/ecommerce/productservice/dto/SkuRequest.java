package com.ecommerce.productservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class SkuRequest {
    // create 时不传（null），update 时必传（标记要修改哪个 SKU）
    private Long id;

    // create 时 @NotEmpty，update 时可选
    private Map<String, String> attrs; // 如 {"颜色":"黑色","尺码":"XL"}

    // create 时 @NotNull，update 时可选
    private BigDecimal price;

    // 仅 create 使用：初始库存
    private Integer stock;

    // 仅 update 使用：true = 删除此 SKU
    private Boolean deleted;
}
