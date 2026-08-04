package com.ecommerce.productservice.service.impl;

import org.springframework.util.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.dto.product.ProductSummaryDTO;
import com.ecommerce.dto.product.SkuDetailDTO;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.productservice.cache.BloomFilterService;
import com.ecommerce.productservice.cache.ProductCacheService;
import com.ecommerce.productservice.dto.*;
import com.ecommerce.productservice.entity.Category;
import com.ecommerce.productservice.entity.Product;
import com.ecommerce.productservice.entity.ProductSKU;
import com.ecommerce.productservice.event.ProductEventPublisher;
import com.ecommerce.productservice.mapper.CategoryMapper;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.mapper.ProductSkuMapper;
import com.ecommerce.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    @Autowired private ProductMapper productMapper;
    @Autowired private ProductSkuMapper skuMapper;
    @Autowired private ProductCacheService cacheService;
    @Autowired private BloomFilterService bloomFilterService;
    @Autowired private RedissonClient redissonClient;
    @Autowired private ProductEventPublisher productEventPublisher;
    @Autowired private CategoryMapper categoryMapper;

    @Override
    public IPage<ProductListResponse> pageQuery(ProductPageQuery query) {
        // Step 1：构建查询条件
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getDeleted, 0)
            .eq(Product::getStatus, 1)           // 仅上架
            .eq(Product::getAuditStatus, 1);     // 仅审核通过
        if (query.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, query.getCategoryId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Product::getName, query.getKeyword());
        }
        if (StringUtils.hasText(query.getBrand())) {
            wrapper.eq(Product::getBrand, query.getBrand());
        }
        if (query.getMinPrice() != null) {
            wrapper.ge(Product::getMinPrice, query.getMinPrice());
        }
        if (query.getMaxPrice() != null) {
            wrapper.le(Product::getMaxPrice, query.getMaxPrice());
        }
        if (StringUtils.hasText(query.getPromoTag())) {
            wrapper.eq(Product::getPromoTag, query.getPromoTag());
        }

        // Step 2：排序
        switch (query.getSort() != null ? query.getSort() : "create_desc") {
            case "sales_desc":
                wrapper.orderByDesc(Product::getSales);
                break;
            case "price_asc":
                wrapper.orderByAsc(Product::getMinPrice);
                break;
            case "price_desc":
                wrapper.orderByDesc(Product::getMaxPrice);
                break;
            default:
                wrapper.orderByDesc(Product::getCreateTime);
        }

        // Step 3：分页
        Page<Product> page = new Page<>(query.getPage(), query.getSize());
        Page<Product> result = productMapper.selectPage(page, wrapper);

        // Step 4：转换为 ProductListResponse（脱敏：不返回 merchant_id）
        return result.convert(this::toListResponse);
    }

    @Override
    public ProductResponse getById(Long id) {

        // Step 1：BloomFilter 预检（防穿透）
        if(!bloomFilterService.mightContain(id)){
            log.debug("BloomFilter 预检未通过，productId={}", id);
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        // Step 2：查多级缓存
        ProductResponse cached = cacheService.get(id);
        if(cached != null){
            return cached;
        }
        // Step 3：缓存未命中 → 加分布式锁防击穿
        RLock lock = redissonClient.getLock("lock:product:" + id);
        try{
            if(lock.tryLock(3,5, TimeUnit.SECONDS)){
                cached = cacheService.get(id);
                if (cached != null)
                    return cached;

                Product product = productMapper.selectById(id);
                if (product == null || product.getDeleted() == 1) {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                }

                List<ProductSKU> skus = skuMapper.selectList(
                    new LambdaQueryWrapper<ProductSKU>()
                        .eq(ProductSKU::getProductId, id)
                );
                ProductResponse vo = assembleResponse(product, skus);
                cacheService.put(id,vo);
                return vo;
            }

        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return null;
    }

    // ==================== create（事务） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductResponse create(ProductCreateRequest request,Long merchantId){
        // Step 0：校验前置条件
        validateCreateRequest(request);

        // Step 1：校验分类存在且启用
        Category category = categoryMapper.selectById(request.getCategoryId());
        if(category==null || category.getStatus() == 0){
            throw new BusinessException(ErrorCode.PRODUCT_CATEGORY_NOT_EXIST);
        }

        // Step 3：校验 SKU 规格不重复
        validateSkuAttrsUnique(request.getSkus());

        // Step 4：计算 minPrice / maxPrice
        List<SkuRequest> skuRequests = request.getSkus();
        BigDecimal minPrice = skuRequests.stream()
            .map(SkuRequest::getPrice).min(BigDecimal::compareTo).get();
        BigDecimal maxPrice = skuRequests.stream()
            .map(SkuRequest::getPrice).max(BigDecimal::compareTo).get();

        // Step 5：插入 product
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setMerchantId(merchantId);
        product.setBrand(request.getBrand());
        product.setMainImage(request.getMainImage());
        product.setImages(request.getImages());
        product.setStatus(0);                    // 新创建默认下架
        product.setAuditStatus(0);               // 待审核
        product.setSales(0L);
        product.setMinPrice(minPrice);
        product.setMaxPrice(maxPrice);
        product.setPromoTag(request.getPromoTag());
        product.setPromoEndTime(request.getPromoEndTime());
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.insert(product);

        // Step 6：批量插入 SKU
        List<ProductSKU> skuList = new ArrayList<>();
        for (SkuRequest sr : skuRequests) {
            ProductSKU sku = new ProductSKU();
            sku.setProductId(product.getId());
            sku.setAttrs(sr.getAttrs());
            sku.setPrice(sr.getPrice());
            sku.setStock(sr.getStock());
            sku.setCreateTime(LocalDateTime.now());
            sku.setUpdateTime(LocalDateTime.now());
            skuList.add(sku);
        }
        skuMapper.insert(skuList);  // MyBatis-Plus 批量插入（逐条，非真批量）

        // Step 7：添加到 BloomFilter
        bloomFilterService.put(product.getId());

        // Step 8：发布事件
        productEventPublisher.publishCreated(product);

        log.info("商品创建成功: productId={}, merchantId={}, skuCount={}",
            product.getId(), merchantId, skuList.size());

        return assembleResponse(product, skuList);
    }


    /** 校验创建请求 */
    private void validateCreateRequest(ProductCreateRequest request) {
        // 图片数量检查
        if (request.getImages() != null && request.getImages().size() > 10) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_TOO_MANY);
        }
        // SKU 非空检查
        if (request.getSkus() == null || request.getSkus().isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_SKU_EMPTY);
        }
        // SKU 数量上限
        if (request.getSkus().size() > 50) {
            throw new BusinessException(ErrorCode.PRODUCT_SKU_TOO_MANY);
        }
    }

    /** 校验同 SPU 下 SKU 规格不重复 */
    private void validateSkuAttrsUnique(List<SkuRequest> skus) {
        Set<String> attrKeys = new HashSet<>();
        for (SkuRequest sku : skus) {
            // 将 Map 转为排序后的字符串，作为唯一性标识
            String key = sku.getAttrs().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
            if (!attrKeys.add(key)) {
                throw new BusinessException(ErrorCode.SKU_ATTRS_DUPLICATE);
            }
        }
    }


    // ==================== update（事务） ====================


    @Override
    public ProductResponse update(Long id ,ProductUpdateRequest request ,Long currentMerchantId){
        // Step 1：查商品 + 校验存在
        Product product = productMapper.selectById(id);
        if(product == null || product.getDeleted() == 1){
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // Step 2：校验归属（商家只能改自己的；ADMIN 可改所有）
        if (!currentMerchantId.equals(product.getMerchantId())) {
            throw new BusinessException(ErrorCode.PRODUCT_NO_PERMISSION);
        }

        // Step 3：若在审核中（auditStatus=0），不可编辑
        if (product.getAuditStatus() == 0) {
            throw new BusinessException(ErrorCode.PRODUCT_CANNOT_EDIT_AUDITED);
        }

        // Step 4：更新非 null 字段
        boolean priceChanged = false;
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getCategoryId() != null) product.setCategoryId(request.getCategoryId());
        if (request.getBrand() != null) product.setBrand(request.getBrand());
        if (request.getMainImage() != null) product.setMainImage(request.getMainImage());
        if (request.getImages() != null) product.setImages(request.getImages());

        // Step 5：如果有 SKU 变更，处理 SKU 的增删改
        List<ProductSKU> updatedSkus = null;
        if (request.getSkus() != null) {
            updatedSkus = processSkuUpdate(id, request.getSkus());
            // 重新计算 min/max price
            BigDecimal minPrice = updatedSkus.stream()
                .map(ProductSKU::getPrice).min(BigDecimal::compareTo).get();
            BigDecimal maxPrice = updatedSkus.stream()
                .map(ProductSKU::getPrice).max(BigDecimal::compareTo).get();
            if (!minPrice.equals(product.getMinPrice()) || !maxPrice.equals(product.getMaxPrice())) {
                priceChanged = true;
            }
            product.setMinPrice(minPrice);
            product.setMaxPrice(maxPrice);
        }

        product.setUpdateTime(LocalDateTime.now());
        product.setAuditStatus(0); // 编辑后需重新审核
        product.setStatus(0);       // 自动下架
        productMapper.updateById(product);

        // Step 6：清除缓存
        cacheService.evict(id);

        // Step 7：发布事件
        productEventPublisher.publishUpdated(product);
        if (priceChanged) {
            productEventPublisher.publishPriceChanged(product, null); // old prices 可由调用方传
        }

        log.info("商品更新成功: productId={}, 触发重新审核", id);

        List<ProductSKU> currentSkus = updatedSkus != null ? updatedSkus
            : skuMapper.selectList(new LambdaQueryWrapper<ProductSKU>().eq(ProductSKU::getProductId, id));
        return assembleResponse(product, currentSkus);

    }

    /** 处理 SKU 的增删改 */
    private List<ProductSKU> processSkuUpdate(Long productId, List<SkuRequest> requests) {
        // 找出标记删除的 SKU ID 集合
        Set<Long> deleteIds = requests.stream()
            .filter(s -> s.getId() != null && Boolean.TRUE.equals(s.getDeleted()))
            .map(SkuRequest::getId)
            .collect(Collectors.toSet());

        // 删除标记的 SKU
        if (!deleteIds.isEmpty()) {
            skuMapper.deleteBatchIds(deleteIds);
        }

        // 处理新增/更新的 SKU
        List<ProductSKU> result = new ArrayList<>();
        for (SkuRequest r : requests) {
            if (Boolean.TRUE.equals(r.getDeleted())) continue; // 跳过已删除的

            ProductSKU sku;
            if (r.getId() != null) {
                // 更新已有 SKU
                sku = skuMapper.selectById(r.getId());
                if (sku == null || !sku.getProductId().equals(productId)) {
                    throw new BusinessException(ErrorCode.SKU_NOT_FOUND);
                }
                if (r.getAttrs() != null) sku.setAttrs(r.getAttrs());
                if (r.getPrice() != null) sku.setPrice(r.getPrice());
                sku.setUpdateTime(LocalDateTime.now());
                skuMapper.updateById(sku);
            } else {
                // 新增 SKU
                sku = new ProductSKU();
                sku.setProductId(productId);
                sku.setAttrs(r.getAttrs());
                sku.setPrice(r.getPrice());
                sku.setStock(0); // 新增 SKU 初始库存 0，由 inventory-service 设置
                sku.setCreateTime(LocalDateTime.now());
                sku.setUpdateTime(LocalDateTime.now());
                skuMapper.insert(sku);
            }
            result.add(sku);
        }
        return result;
    }
    // ==================== updateStatus ====================

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status, Long currentMerchantId) {
        Product product = productMapper.selectById(id);
        if (product == null || product.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        // 权限校验
        if (!currentMerchantId.equals(product.getMerchantId())) {
            throw new BusinessException(ErrorCode.PRODUCT_NO_PERMISSION);
        }
        // 只有审核通过的商品才能上架
        if (status == 1 && product.getAuditStatus() != 1) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AUDITED);
        }

        Integer oldStatus = product.getStatus();
        product.setStatus(status);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        // 清除缓存
        cacheService.evict(id);
        // 发布状态变更事件
        productEventPublisher.publishStatusChanged(product, oldStatus);
        log.info("商品状态变更: productId={}, {} → {}", id,
            oldStatus == 0 ? "下架" : "上架",
            status == 0 ? "下架" : "上架");
    }

    // ==================== audit ====================

    @Override
    @Transactional
    public void audit(Long id, AuditRequest request, Long adminId) {
        Product product = productMapper.selectById(id);
        if (product == null || product.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (product.getAuditStatus() != 0) {
            throw new BusinessException(42013, "该商品已审核过，不可重复审核");
        }

        Integer oldAuditStatus = product.getAuditStatus();
        if (request.getApproved()) {
            product.setAuditStatus(1);     // 通过
            product.setStatus(1);          // 自动上架
            product.setAuditRemark(null);
        } else {
            if (StringUtils.isEmpty(request.getRemark())) {
                throw new BusinessException(42014, "拒绝审核必须填写原因");
            }
            product.setAuditStatus(2);     // 拒绝
            product.setAuditRemark(request.getRemark());
            product.setStatus(0);          // 保持下架
        }
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);

        // 清除缓存
        cacheService.evict(id);

        // 发布状态变更事件 → search-service 更新 ES 索引
        productEventPublisher.publishStatusChanged(product, null);

        log.info("商品审核完成: productId={}, adminId={}, result={}",
            id, adminId, request.getApproved() ? "通过" : "拒绝");
    }

    // ==================== 组装 Response ====================
    private ProductResponse assembleResponse(Product p, List<ProductSKU> skus) {
        ProductResponse vo = new ProductResponse();
        BeanUtils.copyProperties(p, vo);
        vo.setCategoryName(null); // 需要额外查询 category 表填充
        vo.setSkus(skus.stream().map(this::toSkuResponse).collect(Collectors.toList()));
        return vo;
    }

    private SkuResponse toSkuResponse(ProductSKU s) {
        SkuResponse v = new SkuResponse();
        v.setId(s.getId());
        v.setAttrs(s.getAttrs());
        v.setPrice(s.getPrice());
        v.setStock(s.getStock());  // ⚠️ 实际应优先取 Redis 实时库存
        return v;
    }

    private ProductListResponse toListResponse(Product p) {
        ProductListResponse vo = new ProductListResponse();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setMainImage(p.getMainImage());
        vo.setMinPrice(p.getMinPrice());
        vo.setMaxPrice(p.getMaxPrice());
        vo.setSales(p.getSales());
        vo.setPromoTag(p.getPromoTag());
        vo.setPromoEndTime(p.getPromoEndTime());
        return vo;
    }

    // ==================== SKU 详情（内部服务用） ====================

    @Override
    public SkuDetailDTO getSkuDetail(Long skuId) {
        // ① 查 SKU
        ProductSKU sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.SKU_NOT_FOUND);
        }

        // ② 查商品
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || product.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // ③ 检查商品状态：已下架或审核未通过不可加购
        if (product.getStatus() != 1 || product.getAuditStatus() != 1) {
            throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF);
        }

        return SkuDetailDTO.builder()
                .skuId(sku.getId())
                .productId(product.getId())
                .categoryId(product.getCategoryId())
                .productName(product.getName())
                .price(sku.getPrice())
                .image(product.getMainImage())
                .merchantId(product.getMerchantId())
                .stock(sku.getStock())
                .build();
    }

    @Override
    public long countByMerchantAndAuditStatus(Long merchantId, Integer auditStatus) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getDeleted, 0);
        if (merchantId != null) {
            wrapper.eq(Product::getMerchantId, merchantId);
        }
        if (auditStatus != null) {
            wrapper.eq(Product::getAuditStatus, auditStatus);
        }
        return productMapper.selectCount(wrapper);
    }

    @Override
    public ProductSummaryDTO getSummaryById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || product.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return ProductSummaryDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .categoryId(product.getCategoryId())
                .merchantId(product.getMerchantId())
                .mainImage(product.getMainImage())
                .status(product.getStatus())
                .auditStatus(product.getAuditStatus())
                .sales(product.getSales())
                .minPrice(product.getMinPrice())
                .maxPrice(product.getMaxPrice())
                .createTime(product.getCreateTime())
                .build();
    }
}
