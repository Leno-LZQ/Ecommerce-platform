package com.ecommerce.promotionservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.promotionservice.entity.PromotionScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 促销适用范围 Mapper（多态关联表）
 */
@Mapper
public interface PromotionScopeMapper extends BaseMapper<PromotionScope> {

    /**
     * 按目标类型+ID 查适用范围（Layer 2/3 查活动/券的 scope）
     */
    @Select("SELECT * FROM promotion_scope WHERE target_type = #{targetType} AND target_id = #{targetId}")
    List<PromotionScope> selectByTarget(@Param("targetType") String targetType,
                                        @Param("targetId") Long targetId);

    /**
     * 批量：按目标类型+多个目标ID 查 scope
     * （多券/多活动并发匹配时，一次查出避免 N+1）
     */
    @Select("<script>" +
            "SELECT * FROM promotion_scope WHERE target_type = #{targetType} " +
            "AND target_id IN " +
            "<foreach collection='targetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<PromotionScope> selectByTargets(@Param("targetType") String targetType,
                                         @Param("targetIds") List<Long> targetIds);
}
