package com.ecommerce.promotionservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.promotionservice.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 秒杀活动 Mapper
 */
@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /**
     * 查当前进行中的秒杀活动（Layer 1 入口）
     */
    @Select("SELECT * FROM seckill_activity WHERE status = 1 " +
            "AND start_time <= NOW() AND end_time >= NOW()")
    List<SeckillActivity> selectOngoing();

    /**
     * 按 SKU 查当前进行中的秒杀（Layer 1 逐行命中判定时用）
     */
    @Select("SELECT * FROM seckill_activity WHERE sku_id = #{skuId} " +
            "AND status = 1 AND start_time <= NOW() AND end_time >= NOW()")
    SeckillActivity selectOngoingBySkuId(@Param("skuId") Long skuId);

    /**
     * 查即将开始的秒杀（前端预热展示用，ActivityStatusJob 库存预热用）
     */
    @Select("SELECT * FROM seckill_activity WHERE status = 0 AND start_time > NOW() " +
            "ORDER BY start_time ASC LIMIT #{limit}")
    List<SeckillActivity> selectUpcoming(@Param("limit") int limit);

    /**
     * 推进秒杀状态（ActivityStatusJob）
     */
    @Update("UPDATE seckill_activity SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
