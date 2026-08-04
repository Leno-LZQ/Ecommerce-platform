package com.ecommerce.promotionservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.promotionservice.entity.UserCouponTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户优惠标签 Mapper
 */
@Mapper
public interface UserCouponTagMapper extends BaseMapper<UserCouponTag> {

    /**
     * 按用户 ID + 标签类型查标签（幂等检查：注册事件重复消费时不重复打标）
     */
    @Select("SELECT * FROM user_coupon_tag WHERE user_id = #{userId} AND tag_type = #{tagType}")
    UserCouponTag selectByUserAndTagType(@Param("userId") Long userId,
                                         @Param("tagType") String tagType);

    /**
     * 按用户查所有标签
     */
    @Select("SELECT * FROM user_coupon_tag WHERE user_id = #{userId}")
    List<UserCouponTag> selectByUserId(@Param("userId") Long userId);
}
