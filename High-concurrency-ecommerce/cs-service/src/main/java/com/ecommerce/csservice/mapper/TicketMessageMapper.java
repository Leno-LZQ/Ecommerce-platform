package com.ecommerce.csservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.csservice.entity.TicketMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TicketMessageMapper extends BaseMapper<TicketMessage> {

    /**
     * 查询工单消息历史
     */
    @Select("SELECT * FROM ticket_message " +
        "WHERE ticket_id = #{ticketId} " +
        "ORDER BY created_at ASC")
    List<TicketMessage> findByTicketId(@Param("ticketId") Long ticketId);
}
