package com.ecommerce.recommendationservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.recommendationservice.entity.RecoResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RecoResultMapper extends BaseMapper<RecoResult> {

    /**
     * 查询某用户指定策略的 TopN 推荐结果。
     */
    @Select("SELECT * FROM reco_result WHERE user_id = #{userId} AND strategy = #{strategy} " +
            "ORDER BY score DESC LIMIT #{limit}")
    List<RecoResult> selectTopByUserAndStrategy(@Param("userId") Long userId,
                                                 @Param("strategy") String strategy,
                                                 @Param("limit") int limit);

    /**
     * 查询全局热门/新人等无 userId 的结果。
     */
    @Select("SELECT * FROM reco_result WHERE strategy = #{strategy} ORDER BY score DESC LIMIT #{limit}")
    List<RecoResult> selectTopByStrategy(@Param("strategy") String strategy, @Param("limit") int limit);

}
