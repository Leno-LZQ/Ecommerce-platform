package com.ecommerce.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FailedItem {
    private Long skuId;
    private int required;
    private int available;
}
