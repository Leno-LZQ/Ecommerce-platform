package com.ecommerce.merchantservice.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.client.OrderClient;
import com.ecommerce.client.UserClient;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.merchantservice.dto.*;
import com.ecommerce.merchantservice.entity.*;
import com.ecommerce.merchantservice.event.MerchantEventPublisher;
import com.ecommerce.merchantservice.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MerchantService {

    // ========== 状态常量 ==========
    static final int STATUS_PENDING  = 0;
    static final int STATUS_ACTIVE   = 1;
    static final int STATUS_REJECTED = 2;
    static final int STATUS_FROZEN   = 3;
    static final int STATUS_CLOSED   = 4;

    private final MerchantMapper merchantMapper;
    private final MerchantSettlementMapper settlementMapper;
    private final MerchantQualificationMapper qualificationMapper;
    private final MerchantAuditLogMapper auditLogMapper;
    private final MerchantEventPublisher eventPublisher;
    private final OrderClient orderClient;
    private final UserClient userClient;

    // ==================== 商家入驻 ====================
    @Transactional
    public MerchantVO register(Long userId, MerchantRegisterRequest req) {
        boolean exists = merchantMapper.exists(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId));
        if (exists) {
            throw new BusinessException(ErrorCode.MERCHANT_ALREADY_EXISTS.getCode(),
                    ErrorCode.MERCHANT_ALREADY_EXISTS.getMessage());
        }

        long merchantId = IdUtil.getSnowflakeNextId();
        Merchant merchant = new Merchant();
        merchant.setId(merchantId);
        merchant.setUserId(userId);
        merchant.setShopName(req.getShopName());
        merchant.setShopLogo(req.getShopLogo());
        merchant.setShopDesc(req.getShopDesc());
        merchant.setContactName(req.getContactName());
        merchant.setContactPhone(req.getContactPhone());
        merchant.setStatus(STATUS_PENDING);
        merchantMapper.insert(merchant);

        MerchantSettlement settlement = new MerchantSettlement();
        settlement.setId(IdUtil.getSnowflakeNextId());
        settlement.setMerchantId(merchantId);
        settlement.setBankName(req.getBankName());
        settlement.setBankAccount(req.getBankAccount());
        settlement.setAccountHolder(req.getAccountHolder());
        settlementMapper.insert(settlement);

        if (req.getQualifications() != null) {
            for (var item : req.getQualifications()) {
                MerchantQualification qual = new MerchantQualification();
                qual.setId(IdUtil.getSnowflakeNextId());
                qual.setMerchantId(merchantId);
                qual.setQualType(item.getQualType());
                qual.setQualFileUrl(item.getQualFileUrl());
                qual.setStatus(STATUS_PENDING);
                qualificationMapper.insert(qual);
            }
        }

        log.info("商家入驻: merchantId={}, shopName={}", merchantId, req.getShopName());
        return toVO(merchant);
    }

    // ==================== 审核 ====================
    @Transactional
    public MerchantVO audit(Long merchantId, AuditRequest req, Long operatorId) {
        Merchant merchant = findById(merchantId);
        if (merchant.getStatus() != STATUS_PENDING) {
            throw new BusinessException(ErrorCode.MERCHANT_AUDIT_STATUS_INVALID);
        }

        if (Boolean.TRUE.equals(req.getApproved())) {
            changeStatus(merchant, STATUS_ACTIVE, operatorId, req.getRemark());
            eventPublisher.publishApproved(merchant, req.getRemark());
            assignMerchantRole(merchant.getUserId());
        } else {
            changeStatus(merchant, STATUS_REJECTED, operatorId, req.getRemark());
            eventPublisher.publishRejected(merchant, req.getRemark());
        }
        return toVO(merchant);
    }

    /** 审核通过后为用户分配 ROLE_MERCHANT（best-effort，失败仅记日志） */
    private void assignMerchantRole(Long userId) {
        try {
            userClient.assignRole(userId, Map.of("roleName", "ROLE_MERCHANT"));
            log.info("商家角色分配成功: userId={}", userId);
        } catch (Exception e) {
            log.error("商家角色分配失败: userId={}", userId, e);
        }
    }
    // ==================== 冻结 ====================
    @Transactional
    public MerchantVO freeze(Long merchantId, String reason, Long operatorId) {
        Merchant merchant = findById(merchantId);
        if (merchant.getStatus() != STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.MERCHANT_STATUS_INVALID);
        }
        changeStatus(merchant, STATUS_FROZEN, operatorId, reason);
        eventPublisher.publishFrozen(merchant, reason);
        return toVO(merchant);
    }

    // ==================== 解冻 ====================
    @Transactional
    public MerchantVO unfreeze(Long merchantId, Long operatorId) {
        Merchant merchant = findById(merchantId);
        if (merchant.getStatus() != STATUS_FROZEN) {
            throw new BusinessException(ErrorCode.MERCHANT_STATUS_INVALID);
        }
        changeStatus(merchant, STATUS_ACTIVE, operatorId, null);
        eventPublisher.publishUnfrozen(merchant);
        return toVO(merchant);
    }

    // ==================== 注销 ====================
    @Transactional
    public MerchantVO close(Long merchantId, Long operatorId) {
        Merchant merchant = findById(merchantId);
        if (merchant.getStatus() != STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.MERCHANT_STATUS_INVALID);
        }

        try {
            var result = orderClient.countByMerchantAndStatus(merchantId, "PAID");
            if (result != null && result.getData() != null && result.getData() > 0) {
                throw new BusinessException(ErrorCode.MERCHANT_HAS_PENDING_ORDERS);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("校验订单降级: merchantId={}", merchantId, e);
        }

        changeStatus(merchant, STATUS_CLOSED, operatorId, null);
        eventPublisher.publishClosed(merchant);
        return toVO(merchant);
    }

    // ==================== 查询 ====================
    public MerchantVO getByUserId(Long userId) {
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId));
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return toVO(merchant);
    }

    public MerchantVO getById(Long merchantId) {
        return toVO(findById(merchantId));
    }

    public List<MerchantVO> pageByStatus(Integer status, long page, long size) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Merchant::getStatus, status);
        }
        wrapper.orderByDesc(Merchant::getCreateTime);
        wrapper.last("LIMIT " + (page - 1) * size + "," + size);
        return merchantMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<MerchantVO.AuditLogVO> getAuditLogs(Long merchantId) {
        return auditLogMapper.selectList(
                new LambdaQueryWrapper<MerchantAuditLog>()
                        .eq(MerchantAuditLog::getMerchantId, merchantId)
                        .orderByDesc(MerchantAuditLog::getCreateTime))
                .stream()
                .map(log -> MerchantVO.AuditLogVO.builder()
                        .id(log.getId()).operatorId(log.getOperatorId())
                        .fromStatus(log.getFromStatus()).toStatus(log.getToStatus())
                        .remark(log.getRemark()).createTime(log.getCreateTime()).build())
                .collect(Collectors.toList());
    }

    // ==================== 更新店铺信息 ====================
    @Transactional
    public MerchantVO updateShopInfo(Long merchantId, MerchantUpdateRequest req) {
        Merchant merchant = findById(merchantId);
        if (merchant.getStatus() != STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.MERCHANT_STATUS_INVALID);
        }
        if (req.getShopName() != null) merchant.setShopName(req.getShopName());
        if (req.getShopLogo() != null) merchant.setShopLogo(req.getShopLogo());
        if (req.getShopDesc() != null) merchant.setShopDesc(req.getShopDesc());
        if (req.getContactName() != null) merchant.setContactName(req.getContactName());
        if (req.getContactPhone() != null) merchant.setContactPhone(req.getContactPhone());
        merchantMapper.updateById(merchant);
        return toVO(merchant);
    }

    // ==================== 资质逐条审核 ====================
    @Transactional
    public void auditQualification(Long qualId, boolean approved, String remark) {
        MerchantQualification qual = qualificationMapper.selectById(qualId);
        if (qual == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND.getCode(), "资质文件不存在");
        }
        qual.setStatus(approved ? STATUS_ACTIVE : STATUS_REJECTED);
        qualificationMapper.updateById(qual);
    }

    // ==================== Feign 内部端点 ====================
    public long countAll() {
        return merchantMapper.selectCount(null);
    }

    public long countPending() {
        return merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getStatus, STATUS_PENDING));
    }

    public boolean exists(Long merchantId) {
        return merchantMapper.selectById(merchantId) != null;
    }

    /** 获取所有商家 ID 列表（供批量查询等内部场景） */
    public List<Long> listAllIds() {
        return merchantMapper.selectObjs(
                new LambdaQueryWrapper<Merchant>().select(Merchant::getId))
                .stream()
                .map(o -> (Long) o)
                .toList();
    }

    // ==================== 私有方法 ====================
    void changeStatus(Merchant merchant, int newStatus, Long operatorId, String remark) {
        int oldStatus = merchant.getStatus();
        merchant.setStatus(newStatus);
        if (remark != null) merchant.setAuditRemark(remark);
        merchantMapper.updateById(merchant);

        MerchantAuditLog log = new MerchantAuditLog();
        log.setId(IdUtil.getSnowflakeNextId());
        log.setMerchantId(merchant.getId());
        log.setOperatorId(operatorId);
        log.setFromStatus(oldStatus);
        log.setToStatus(newStatus);
        log.setRemark(remark);
        auditLogMapper.insert(log);
    }

    Merchant findById(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        return merchant;
    }

    MerchantVO toVO(Merchant m) {
        MerchantSettlement s = settlementMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlement>().eq(MerchantSettlement::getMerchantId, m.getId()));
        List<MerchantQualification> quals = qualificationMapper.selectList(
                new LambdaQueryWrapper<MerchantQualification>().eq(MerchantQualification::getMerchantId, m.getId()));

        return MerchantVO.builder()
                .id(m.getId()).userId(m.getUserId())
                .shopName(m.getShopName()).shopLogo(m.getShopLogo()).shopDesc(m.getShopDesc())
                .contactName(m.getContactName()).contactPhone(maskPhone(m.getContactPhone()))
                .status(m.getStatus()).auditRemark(m.getAuditRemark())
                .bankName(s != null ? s.getBankName() : null)
                .bankAccountMasked(s != null ? maskBankAccount(s.getBankAccount()) : null)
                .accountHolder(s != null ? s.getAccountHolder() : null)
                .qualifications(quals.stream()
                        .map(q -> MerchantVO.QualificationVO.builder()
                                .id(q.getId()).qualType(q.getQualType())
                                .qualFileUrl(q.getQualFileUrl()).status(q.getStatus()).build())
                        .collect(Collectors.toList()))
                .createTime(m.getCreateTime()).updateTime(m.getUpdateTime())
                .build();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskBankAccount(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
