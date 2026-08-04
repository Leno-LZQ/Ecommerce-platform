package com.ecommerce.csservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.csservice.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {

    /**
     * 按工单号查询
     */
    @Select("SELECT * FROM ticket WHERE ticket_no = #{ticketNo} LIMIT 1")
    Ticket findByTicketNo(@Param("ticketNo") String ticketNo);

    /**
     * 查询用户的工单列表
     */
    @Select("SELECT * FROM ticket WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Ticket> findByUserId(@Param("userId") Long userId);

    /**
     * 查询客服的工单池（待分配 + 我的工单）
     */
    @Select("SELECT * FROM ticket " +
        "WHERE status = 'CREATED' OR assigned_agent_id = #{agentId} " +
        "ORDER BY " +
        "  CASE status WHEN 'CREATED' THEN 0 ELSE 1 END ASC, " +
        "  priority ASC, " +
        "  created_at ASC")
    List<Ticket> findAgentPool(@Param("agentId") Long agentId);

    /**
     * 查询客服当前处理中的工单数
     */
    @Select("SELECT COUNT(*) FROM ticket " +
        "WHERE assigned_agent_id = #{agentId} " +
        "  AND status NOT IN ('RESOLVED', 'CLOSED')")
    int countByAgentId(@Param("agentId") Long agentId);

    /**
     * 自动分配工单
     */
    @Update("UPDATE ticket SET status = 'ASSIGNED', assigned_agent_id = #{agentId}, updated_at = NOW() " +
        "WHERE id = #{ticketId} AND status = 'CREATED'")
    int assignTicket(@Param("ticketId") Long ticketId, @Param("agentId") Long agentId);

    /**
     * 查找关联订单的未关闭纠纷工单
     */
    @Select("SELECT * FROM ticket " +
        "WHERE order_id = #{orderId} " +
        "  AND category IN ('ORDER_DISPUTE', 'REFUND') " +
        "  AND status NOT IN ('RESOLVED', 'CLOSED') " +
        "LIMIT 1")
    Ticket findOpenDisputeByOrderId(@Param("orderId") Long orderId);
}
