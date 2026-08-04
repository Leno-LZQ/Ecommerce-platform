package com.ecommerce.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "收货人不能为空")
    private String consignee;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "收货地址不能为空")
    private String address;

    /** 用户备注 */
    private String remark;

    /** 使用的优惠券码列表（可选） */
    private List<String> couponCodes;

    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<OrderItemDTO> items;

    // ─── 内嵌 DTO，不暴露实体类 ───

    @Data
    public static class OrderItemDTO {

        @NotNull(message = "SKU ID 不能为空")
        private Long skuId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量至少为1")
        private Integer quantity;
    }
}
