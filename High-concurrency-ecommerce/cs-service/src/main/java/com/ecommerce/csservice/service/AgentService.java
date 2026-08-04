package com.ecommerce.csservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.csservice.entity.CsAgent;
import com.ecommerce.csservice.entity.enums.AgentRole;
import com.ecommerce.csservice.entity.enums.AgentStatus;
import com.ecommerce.csservice.mapper.CsAgentMapper;
import com.ecommerce.csservice.mapper.TicketMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客服 Agent 服务
 *
 * <p>管理客服在线状态、负载，以及智能分配。</p>
 */
@Slf4j
@Service
public class AgentService {

    private final CsAgentMapper csAgentMapper;
    private final TicketMapper ticketMapper;

    public AgentService(CsAgentMapper csAgentMapper, TicketMapper ticketMapper) {
        this.csAgentMapper = csAgentMapper;
        this.ticketMapper = ticketMapper;
    }

    /**
     * 根据用户 ID 查询客服身份
     */
    public CsAgent getByUserId(Long userId) {
        QueryWrapper<CsAgent> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        return csAgentMapper.selectOne(wrapper);
    }

    /**
     * 根据客服 ID 查询
     */
    public CsAgent getById(Long agentId) {
        return csAgentMapper.selectById(agentId);
    }

    /**
     * 查询所有在线客服
     */
    public List<CsAgent> listOnlineAgents() {
        QueryWrapper<CsAgent> wrapper = new QueryWrapper<>();
        wrapper.eq("status", AgentStatus.ONLINE.getCode());
        return csAgentMapper.selectList(wrapper);
    }

    /**
     * 查找最佳分配客服
     *
     * <p>策略：在线客服中，当前处理工单数最少者；无则返回 MANAGER 兜底。</p>
     */
    public CsAgent findBestAgent(String category) {
        List<CsAgent> onlineAgents = csAgentMapper.findOnlineAgents();
        if (onlineAgents.isEmpty()) {
            // 无在线客服，找主管兜底
            QueryWrapper<CsAgent> wrapper = new QueryWrapper<>();
            wrapper.eq("role", AgentRole.MANAGER.getCode())
                .orderByAsc("max_concurrent")
                .last("LIMIT 1");
            return csAgentMapper.selectOne(wrapper);
        }

        CsAgent best = null;
        int minLoad = Integer.MAX_VALUE;
        for (CsAgent agent : onlineAgents) {
            int load = ticketMapper.countByAgentId(agent.getId());
            if (load < agent.getMaxConcurrent() && load < minLoad) {
                minLoad = load;
                best = agent;
            }
        }
        return best;
    }

    /**
     * 更新客服在线状态
     */
    public void updateStatus(Long userId, String status) {
        CsAgent agent = getByUserId(userId);
        if (agent == null) {
            log.warn("用户 {} 不是客服，无法更新状态", userId);
            return;
        }
        agent.setStatus(status);
        csAgentMapper.updateById(agent);
    }
}
