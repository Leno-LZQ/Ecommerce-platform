package com.ecommerce.productservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.productservice.entity.ProductSKU;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSKU> {
}
