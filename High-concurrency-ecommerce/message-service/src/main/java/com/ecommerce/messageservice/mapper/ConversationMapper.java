package com.ecommerce.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.messageservice.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    // 自定义方法：查找某个买家和某个商家在某个商品/订单下的会话
    // 这个方法需要手写 SQL，因为 BaseMapper 没有内置这种复杂查询
    @Select("SELECT * " +
        "FROM conversation " +
        "WHERE user_id = #{userId} " +
        "  AND merchant_id = #{merchantId} " +
        "  AND (product_id = #{productId} OR #{productId} IS NULL) " +
        "  AND (order_id = #{orderId} OR #{orderId} IS NULL) " +
        "ORDER BY updated_at DESC " +
        "LIMIT 1")
    Conversation findByUserAndMerchant(
        @Param("userId") Long userId,
        @Param("merchantId") Long merchantId,
        @Param("productId") Long productId,
        @Param("orderId") Long orderId
    );

}
