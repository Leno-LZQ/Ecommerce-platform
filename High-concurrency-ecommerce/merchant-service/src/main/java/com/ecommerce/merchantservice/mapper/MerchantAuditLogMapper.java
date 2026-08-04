package com.ecommerce.merchantservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.merchantservice.entity.MerchantAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantAuditLogMapper extends BaseMapper<MerchantAuditLog> {
}
