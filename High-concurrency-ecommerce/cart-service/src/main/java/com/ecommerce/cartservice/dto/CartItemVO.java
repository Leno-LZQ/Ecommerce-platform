package com.ecommerce.cartservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车单品前端展示对象。由 CartItemSnapshot + 实时校验生成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemVO {
    private Long skuId;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private String image;
    private Integer quantity;
    private Boolean checked;
    private LocalDateTime snapshotTime;
    private Long merchantId;
}
