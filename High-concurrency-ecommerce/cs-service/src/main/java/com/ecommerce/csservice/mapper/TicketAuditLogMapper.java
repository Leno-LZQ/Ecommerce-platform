package com.ecommerce.csservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.csservice.entity.TicketAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TicketAuditLogMapper extends BaseMapper<TicketAuditLog> {

    /**
     * 查询工单审计日志
     */
    @Select("SELECT * FROM ticket_audit_log " +
        "WHERE ticket_id = #{ticketId} " +
        "ORDER BY created_at ASC")
    List<TicketAuditLog> findByTicketId(@Param("ticketId") Long ticketId);
}
