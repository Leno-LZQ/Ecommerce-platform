package com.ecommerce.recommendationservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.recommendationservice.entity.RecoStrategy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RecoStrategyMapper extends BaseMapper<RecoStrategy> {

    @Select("SELECT * FROM reco_strategy WHERE enabled = 1")
    List<RecoStrategy> selectEnabled();

}
