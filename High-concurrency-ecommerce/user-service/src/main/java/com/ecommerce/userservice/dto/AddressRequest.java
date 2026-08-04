package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressRequest {

    @NotBlank(message = "收件人不能为空")
    @Size(max = 30, message = "收件人最长30个字符")
    private String receiverName;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20, message = "联系电话最长20个字符")
    private String phone;

    @NotBlank(message = "省份不能为空")
    private String province;

    @NotBlank(message = "城市不能为空")
    private String city;

    @NotBlank(message = "区县不能为空")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 200, message = "详细地址最长200个字符")
    private String detail;

    private Integer isDefault;    // 0/1，不传默认0
}
