package com.ecommerce.merchantservice.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantUpdateRequest {

    @Size(max = 100)
    private String shopName;

    @Size(max = 500)
    private String shopLogo;

    @Size(max = 500)
    private String shopDesc;

    @Size(max = 50)
    private String contactName;

    @Size(max = 20)
    private String contactPhone;
}
