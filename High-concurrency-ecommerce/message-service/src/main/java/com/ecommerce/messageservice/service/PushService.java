package com.ecommerce.messageservice.service;

import com.alibaba.fastjson2.JSON;
import com.ecommerce.messageservice.dto.WebSocketFrame;
import com.ecommerce.messageservice.entity.enums.FrameType;
import com.ecommerce.messageservice.entity.Message;
import com.ecommerce.messageservice.entity.PushRecord;
import com.ecommerce.messageservice.mapper.MessageMapper;
import com.ecommerce.messageservice.mapper.PushRecordMapper;
import com.ecommerce.messageservice.websocket.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PushService 离线消息推送服务
 *
 * <p>核心职责：当接收方不在线时，消息已经在 MessageService.sendMessage() 里写了 push_record 表。
 * 本服务的任务就是"把积压的消息送达"。</p>
 *
 * <p>两个入口：</p>
 * <ol>
 *   <li>用户上线时（afterConnectionEstablished 中调用）— 立即推送所有离线消息</li>
 *   <li>定时任务（PushRetryJob）— 扫描推送失败的记录，按阶梯退避策略重试</li>
 * </ol>
 *
 * <p>可靠性原则：推送失败不抛异常，只改状态。由定时任务兜底重试。</p>
 */
@Slf4j
@Service
public class PushService {

    private final PushRecordMapper pushRecordMapper;
    private final MessageMapper messageMapper;
    private final SessionManager sessionManager;

    // 构造函数注入
    public PushService(PushRecordMapper pushRecordMapper,
                       MessageMapper messageMapper,
                       SessionManager sessionManager) {
        this.pushRecordMapper = pushRecordMapper;
        this.messageMapper = messageMapper;
        this.sessionManager = sessionManager;
    }

    // ================================================================
    //  入口一：用户上线时调用
    // ================================================================

    /**
     * 用户上线时，推送该用户所有的离线消息
     *
     * <p>调用时机：MessageWebSocketHandler.afterConnectionEstablished() 的最后。</p>
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>查 push_record 表中该用户所有 push_status=0（待推送）的记录</li>
     *   <li>逐条查出对应的原始消息（从 message 表）</li>
     *   <li>构建 WebSocketFrame → JSON → TextMessage → 发送</li>
     *   <li>发送成功 → update push_status=1（已推送）</li>
     *   <li>发送失败 → update push_status=2（失败，等待定时任务重试）</li>
     * </ol>
     *
     * <p>注意：这里没有用 @Transactional 包裹整批，
     * 因为一条推送失败不应连累其他消息。<p>
     *
     * @param userId  刚上线的用户 ID
     * @param session 用户当前的 WebSocket 连接
     */
    public void pushOfflineMessages(Long userId, WebSocketSession session) {
        // ----------------------------------------------------------
        // 第①步：查待推送记录
        // SQL: SELECT * FROM push_record WHERE user_id = ? AND push_status = 0
        // ----------------------------------------------------------
        List<PushRecord> pendingList = pushRecordMapper.findPendingByUserId(userId);

        if (pendingList.isEmpty()) {
            log.debug("用户 {} 无离线消息", userId);
            return;
        }

        log.info("用户 {} 有 {} 条离线消息待推送", userId, pendingList.size());

        // ----------------------------------------------------------
        // 第②步：逐条推送
        // ----------------------------------------------------------
        int successCount = 0;
        int failCount = 0;

        for (PushRecord record : pendingList) {

            // ----------------------------------------------------------
            // ②-1: 查出原始消息内容
            // ----------------------------------------------------------
            Message msg = messageMapper.selectById(record.getMessageId());
            if (msg == null) {
                // 消息已被删除（极端情况），直接标记为已推送跳过
                record.setPushStatus(1);
                pushRecordMapper.updateById(record);
                continue;
            }

            // ----------------------------------------------------------
            // ②-2: 构建推送帧（和 MessageService 中在线推送的格式一致）
            // ----------------------------------------------------------
            WebSocketFrame frame = new WebSocketFrame();
            frame.setType(FrameType.MESSAGE);
            frame.setMessageId(msg.getId());
            frame.setConversationId(msg.getConversationId());
            frame.setContent(msg.getContent());
            frame.setMessageType(msg.getMessageType());
            frame.setTimestamp(System.currentTimeMillis());

            String frameJson = JSON.toJSONString(frame);

            // ----------------------------------------------------------
            // ②-3: 尝试发送
            // ----------------------------------------------------------
            try {
                session.sendMessage(new TextMessage(frameJson));

                // 推送成功 → 标记为 1
                record.setPushStatus(1);
                record.setUpdateTime(LocalDateTime.now());
                pushRecordMapper.updateById(record);
                successCount++;

            } catch (IOException e) {
                // 推送失败 → 标记为 2（等待定时任务重试）
                // 注意：不抛异常，不中断后续消息的推送
                log.warn("离线消息推送失败: messageId={}, userId={}, reason={}",
                    record.getMessageId(), userId, e.getMessage());

                record.setPushStatus(2);
                record.setUpdateTime(LocalDateTime.now());
                pushRecordMapper.updateById(record);
                failCount++;
            }
        }

        log.info("用户 {} 离线消息推送完成: 成功 {} 条, 失败 {} 条",
            userId, successCount, failCount);
    }

    // ================================================================
    //  入口二：定时任务批量重试
    // ================================================================

    /**
     * 重试所有"推送失败"的离线消息
     *
     * <p>调用时机：PushRetryJob 定时任务（如每 30 秒一次）。</p>
     * <p>重试策略（阶梯退避）：</p>
     * <pre>
     *   第 0 次失败 → 等 20 秒后重试
     *   第 1 次失败 → 等 60 秒后重试
     *   第 2 次失败 → 等 300 秒后重试
     *   3 次全部失败 → 放弃，告警
     * </pre>
     *
     * <p>调用方（PushRetryJob）传入 maxRetry=3，本方法只推"到了重试时间"的记录。</p>
     *
     * @param maxRetry 最大重试次数（超过则放弃）
     */
    public void retryFailedPushes(int maxRetry) {
        // ----------------------------------------------------------
        // 第①步：查出所有需要重试的记录
        // SQL: SELECT * FROM push_record WHERE push_status = 2 AND retry_count < ?
        // ----------------------------------------------------------
        List<PushRecord> failedList = pushRecordMapper.findFailedRecords(maxRetry);

        if (failedList.isEmpty()) {
            return;  // 没有需要重试的记录，直接返回
        }

        log.info("开始批量重试离线推送: 共 {} 条待重试记录", failedList.size());

        for (PushRecord record : failedList) {

            // ----------------------------------------------------------
            // 第②步：检查是否到了重试时间（阶梯退避）
            // ----------------------------------------------------------
            if (!isTimeToRetry(record)) {
                continue;  // 还没到下一次重试时间，跳过
            }

            // ----------------------------------------------------------
            // 第③步：检查目标用户当前是否在线
            // ----------------------------------------------------------
            boolean online = sessionManager.isUserOnline(record.getUserId());

            if (!online) {
                // 还是不在线 → retry_count + 1，等下次定时任务再试
                record.setRetryCount(record.getRetryCount() + 1);
                record.setUpdateTime(LocalDateTime.now());
                pushRecordMapper.updateById(record);

                log.debug("用户 {} 仍不在线，消息 {} 重试次数 +1 → {}",
                    record.getUserId(), record.getMessageId(), record.getRetryCount());
                continue;
            }

            // ----------------------------------------------------------
            // 第④步：用户在线了！重新推送
            // ----------------------------------------------------------
            Message msg = messageMapper.selectById(record.getMessageId());
            if (msg == null) {
                // 消息被删了，直接标记已推送
                record.setPushStatus(1);
                record.setUpdateTime(LocalDateTime.now());
                pushRecordMapper.updateById(record);
                continue;
            }

            WebSocketFrame frame = buildPushFrame(msg);
            String frameJson = JSON.toJSONString(frame);

            try {
                sessionManager.sendToUser(record.getUserId(), frameJson);

                // 推送成功 → 标记为 1
                record.setPushStatus(1);
                record.setUpdateTime(LocalDateTime.now());
                pushRecordMapper.updateById(record);

                log.info("离线推送重试成功: messageId={}, userId={}",
                    record.getMessageId(), record.getUserId());

            } catch (Exception e) {
                // 推送失败 → retry_count + 1
                // 下次定时任务会再次尝试，直到超过 maxRetry
                record.setRetryCount(record.getRetryCount() + 1);
                record.setUpdateTime(LocalDateTime.now());
                pushRecordMapper.updateById(record);

                log.warn("离线推送重试失败: messageId={}, userId={}, retryCount={}",
                    record.getMessageId(), record.getUserId(), record.getRetryCount());
            }
        }
    }

    // ================================================================
    //  辅助方法
    // ================================================================

    /**
     * 构建推送用的 WebSocketFrame
     *
     * <p>与 MessageService.sendMessage() 中在线推送的格式完全一致，
     * 确保前端无论是"实时收到"还是"离线补推"，看到的消息帧结构相同。</p>
     */
    private WebSocketFrame buildPushFrame(Message msg) {
        WebSocketFrame frame = new WebSocketFrame();
        frame.setType(FrameType.MESSAGE);
        frame.setMessageId(msg.getId());
        frame.setConversationId(msg.getConversationId());
        frame.setContent(msg.getContent());
        frame.setMessageType(msg.getMessageType());
        frame.setTimestamp(System.currentTimeMillis());
        return frame;
    }

    /**
     * 判断当前时间是否已经到了该条记录的下一次重试时间
     *
     * <p>阶梯退避规则（从上次更新时间算起）：</p>
     * <pre>
     *   retryCount = 0  → 等 20 秒
     *   retryCount = 1  → 等 60 秒
     *   retryCount = 2  → 等 300 秒（5 分钟）
     * </pre>
     *
     * <p>设计思想：如果推送失败是因为对方不在线，那重试越频繁越浪费资源。
     * 不如等久一点，给对方上线的时间。</p>
     */
    private boolean isTimeToRetry(PushRecord record) {
        // 阶梯退避时间表（秒）
        long[] backoffSeconds = {20, 60, 300};

        // 防止数组越界：取 retryCount 和数组末尾的较小值
        int idx = Math.min(record.getRetryCount(), backoffSeconds.length - 1);
        long waitSeconds = backoffSeconds[idx];

        // 上次更新时间 + 等待时间 = 最早可重试时间
        LocalDateTime lastUpdate = record.getUpdateTime();
        LocalDateTime nextRetryTime = lastUpdate.plusSeconds(waitSeconds);

        return LocalDateTime.now().isAfter(nextRetryTime);
    }

}
