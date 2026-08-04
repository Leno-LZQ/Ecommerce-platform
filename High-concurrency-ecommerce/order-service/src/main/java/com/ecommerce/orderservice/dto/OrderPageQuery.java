package com.ecommerce.orderservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderPageQuery {

    /** 买家：userId；商家：merchantId——由 Controller 从 header 安全注入，不由前端传 */
    private Long userId;
    private Long merchantId;

    /** 订单号模糊搜索 */
    private String orderNo;

    /** 订单状态：0=待支付 1=已支付 2=已发货 3=已完成 4=已取消 5=已退款，null=全部 */
    private Integer status;

    /** 商品名/收货人模糊搜索 */
    private String keyword;

    /** 支付方式：null=全部，1=支付宝，2=微信 */
    private Integer payType;

    /** 下单时间区间 */
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 分页，默认 page=1 size=10 */
    private Integer page;
    private Integer size;
}
