package com.ecommerce.csservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.csservice.entity.CsAgent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CsAgentMapper extends BaseMapper<CsAgent> {

    /**
     * 查找某客服专长的在线客服（按 category 匹配，目前仅 role=AGENT/SENIOR 都算可分配）
     */
    @Select("SELECT * FROM cs_agent " +
        "WHERE status = 'ONLINE' " +
        "ORDER BY max_concurrent ASC, id ASC")
    List<CsAgent> findOnlineAgents();

    /**
     * 统计客服当前处理的未关闭工单数
     */
    @Select("SELECT COUNT(*) FROM ticket " +
        "WHERE assigned_agent_id = #{agentId} " +
        "  AND status NOT IN ('RESOLVED', 'CLOSED')")
    int countActiveTickets(@Param("agentId") Long agentId);
}
