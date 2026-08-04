package com.ecommerce.productservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.dto.product.ProductSummaryDTO;
import com.ecommerce.dto.product.SkuDetailDTO;
import com.ecommerce.productservice.dto.*;

public interface ProductService {

    /** 分页查询商品列表（不走缓存） */
    IPage<ProductListResponse> pageQuery(ProductPageQuery query);

    /** 查询商品详情（走多级缓存） */
    ProductResponse getById(Long id);

    /** 查询商品摘要（供 ES 索引同步等内部 Feign 调用） */
    ProductSummaryDTO getSummaryById(Long id);

    /** 创建商品（含SKU批量创建，事务） */
    ProductResponse create(ProductCreateRequest request, Long merchantId);

    /** 更新商品（校验归属权限） */
    ProductResponse update(Long id, ProductUpdateRequest request, Long currentMerchantId);

    /** 上架/下架 */
    void updateStatus(Long id, Integer status, Long currentMerchantId);

    /** 审核（仅 ADMIN） */
    void audit(Long id, AuditRequest request, Long adminId);

    /** 按 SKU ID 查询详情（含商品名、图片、商家 ID），供 cart-service 内部调用 */
    SkuDetailDTO getSkuDetail(Long skuId);

    /** 按商家ID + 审核状态统计商品数（Feign 内部调用） */
    long countByMerchantAndAuditStatus(Long merchantId, Integer auditStatus);
}
