package com.ecommerce.promotionservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.promotionservice.entity.PromotionRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 促销阶梯规则 Mapper
 */
@Mapper
public interface PromotionRuleMapper extends BaseMapper<PromotionRule> {

    /**
     * 按活动 ID 查规则（Layer 2 匹配阶梯时用，按 sort_order 排序）
     */
    @Select("SELECT * FROM promotion_rule WHERE activity_id = #{activityId} ORDER BY sort_order")
    List<PromotionRule> selectByActivityId(@Param("activityId") Long activityId);

    /**
     * 批量按活动 ID 查规则（多活动并发匹配时减少查库次数）
     */
    @Select("<script>" +
            "SELECT * FROM promotion_rule WHERE activity_id IN " +
            "<foreach collection='activityIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " ORDER BY activity_id, sort_order" +
            "</script>")
    List<PromotionRule> selectByActivityIds(@Param("activityIds") List<Long> activityIds);
}
