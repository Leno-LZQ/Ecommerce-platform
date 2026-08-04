package com.ecommerce.orderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.orderservice.entity.OrderSplit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderSplitMapper extends BaseMapper<OrderSplit> {
}
