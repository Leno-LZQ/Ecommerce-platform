package com.ecommerce.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.messageservice.entity.PushRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PushRecordMapper extends BaseMapper<PushRecord> {

    /**
     * 查询某用户所有"待推送"的离线消息（push_status = 0）
     *
     * @param userId 用户 ID
     * @return 待推送记录列表
     */
    @Select("SELECT * FROM push_record " +
        "WHERE user_id = #{userId} " +
        "  AND push_status = 0 " +
        "ORDER BY created_at ASC")
    List<PushRecord> findPendingByUserId(Long userId);

    /**
     * 查询所有"推送失败"且不超过最大重试次数的记录
     * （用于定时任务扫表重试）
     *
     * @param maxRetry 最大重试次数
     * @return 需要重试的记录列表
     */
    @Select("SELECT * FROM push_record " +
        "WHERE push_status = 2 " +
        "  AND retry_count < #{maxRetry} " +
        "ORDER BY created_at ASC")
    List<PushRecord> findFailedRecords(Integer maxRetry);

}
