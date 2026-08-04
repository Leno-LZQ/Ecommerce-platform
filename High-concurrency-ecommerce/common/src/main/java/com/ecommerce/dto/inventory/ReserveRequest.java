package com.ecommerce.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveRequest {
    @NotBlank
    private String orderNo;
    @NotEmpty
    private List<ReserveItem> items;
}
