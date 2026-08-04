package com.ecommerce.recommendationservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.recommendationservice.entity.UserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserBehaviorMapper extends BaseMapper<UserBehavior> {

    /**
     * 查询指定时间窗口内的行为记录。
     */
    @Select("SELECT * FROM user_behavior WHERE created_at >= #{start} AND created_at < #{end}")
    List<UserBehavior> selectByTimeWindow(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

}
