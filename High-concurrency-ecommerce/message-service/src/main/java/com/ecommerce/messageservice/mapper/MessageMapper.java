package com.ecommerce.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.messageservice.entity.enums.SenderType;
import com.ecommerce.messageservice.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    // 自定义方法：查询某个会话的历史消息（分页，按时间倒序）
    @Select("SELECT * FROM message" +
        " WHERE conversation_id = #{conversationId} " +
        "ORDER BY created_at " +
        "DESC LIMIT #{offset},#{size}")
    List<Message> findByConversationId(
        Long conversationId,
        Integer offset,
        Integer size
    );

    /**
     * 批量标记会话消息为已读
     *
     * <p>只标记"对方发的"消息（排除 excludeType），不标记自己发的。</p>
     * <p>MyBatis-Plus 的 EnumTypeHandler 会自动把 SenderType 枚举转为 name() 字符串
     * （USER/MERCHANT/SYSTEM），与数据库 VARCHAR 列的存储值一致。</p>
     *
     * @param conversationId 会话 ID
     * @param excludeType    排除的类型（自己发的不用标记）
     */
    @Update("UPDATE message SET is_read = 1 " +
        "WHERE conversation_id = #{conversationId} " +
        "  AND sender_type != #{excludeType} " +
        "  AND is_read = 0")
    void markAllAsRead(Long conversationId, SenderType excludeType);

}
