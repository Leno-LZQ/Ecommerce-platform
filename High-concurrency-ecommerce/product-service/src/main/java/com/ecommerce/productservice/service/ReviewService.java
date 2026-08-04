package com.ecommerce.productservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.productservice.dto.ReviewResponse;
import com.ecommerce.productservice.dto.ReviewSubmitRequest;
import com.ecommerce.productservice.dto.ReviewSummaryResponse;

public interface ReviewService {

    /** 分页查询商品评价（仅 status=1） */
    IPage<ReviewResponse> pageByProduct(Long productId, int page, int size, Integer score);

    /** 提交评价 */
    ReviewResponse submit(Long productId, ReviewSubmitRequest request, Long userId);


    /** 获取商品评价汇总 */
    ReviewSummaryResponse getSummary(Long productId);

}
