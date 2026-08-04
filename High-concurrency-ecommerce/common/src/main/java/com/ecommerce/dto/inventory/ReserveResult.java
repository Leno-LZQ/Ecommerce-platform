package com.ecommerce.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveResult {
    private boolean success;
    private String orderNo;
    private List<FailedItem> failedItems;  // success=false 时有值
}
