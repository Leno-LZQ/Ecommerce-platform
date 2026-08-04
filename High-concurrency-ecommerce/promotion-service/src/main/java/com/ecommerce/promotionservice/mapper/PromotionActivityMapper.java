package com.ecommerce.promotionservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.promotionservice.entity.PromotionActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 促销活动 Mapper
 */
@Mapper
public interface PromotionActivityMapper extends BaseMapper<PromotionActivity> {

    /**
     * 查当前进行中的活动（Layer 2 匹配入口）
     * JOIN promotion_scope 由 service 层处理，这里先返回活动本体
     */
    @Select("SELECT * FROM promotion_activity WHERE status = 1 " +
            "AND start_time <= NOW() AND end_time >= NOW()")
    List<PromotionActivity> selectOngoing();

    /**
     * 原子扣减预算（lock 成功后同步 MySQL，不依赖 Redis 值）
     */
    @Update("UPDATE promotion_activity SET remaining_budget = remaining_budget - #{amount} " +
            "WHERE id = #{id} AND remaining_budget >= #{amount}")
    int deductBudget(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 回补预算（release/refund 时调用）
     */
    @Update("UPDATE promotion_activity SET remaining_budget = remaining_budget + #{amount} " +
            "WHERE id = #{id}")
    int addBackBudget(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 推进活动状态（ActivityStatusJob）
     */
    @Update("UPDATE promotion_activity SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
