package com.ecommerce.merchantservice.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.merchantservice.dto.SubAccountAddRequest;
import com.ecommerce.merchantservice.dto.SubAccountVO;
import com.ecommerce.merchantservice.entity.Merchant;
import com.ecommerce.merchantservice.entity.MerchantSubAccount;
import com.ecommerce.merchantservice.mapper.MerchantMapper;
import com.ecommerce.merchantservice.mapper.MerchantSubAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MerchantSubAccountService {

    private static final int MAX_SUB_ACCOUNTS = 30;
    private static final Set<String> VALID_ROLES = Set.of("运营", "客服", "财务");

    private final MerchantSubAccountMapper subAccountMapper;
    private final MerchantMapper merchantMapper;

    public List<SubAccountVO> listByMerchant(Long merchantId) {
        return subAccountMapper.selectList(
                new LambdaQueryWrapper<MerchantSubAccount>()
                        .eq(MerchantSubAccount::getMerchantId, merchantId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public SubAccountVO add(Long merchantId, SubAccountAddRequest req) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || merchant.getStatus() != MerchantService.STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.MERCHANT_STATUS_INVALID);
        }
        if (merchant.getUserId().equals(req.getUserId())) {
            throw new BusinessException(ErrorCode.MERCHANT_SELF_AS_SUB_ACCOUNT);
        }
        long count = subAccountMapper.selectCount(
                new LambdaQueryWrapper<MerchantSubAccount>().eq(MerchantSubAccount::getMerchantId, merchantId));
        if (count >= MAX_SUB_ACCOUNTS) {
            throw new BusinessException(ErrorCode.SUB_ACCOUNT_LIMIT_EXCEEDED);
        }
        if (!VALID_ROLES.contains(req.getRole())) {
            throw new BusinessException(ErrorCode.SUB_ACCOUNT_ROLE_INVALID);
        }
        boolean exists = subAccountMapper.exists(
                new LambdaQueryWrapper<MerchantSubAccount>().eq(MerchantSubAccount::getUserId, req.getUserId()));
        if (exists) {
            throw new BusinessException(ErrorCode.SUB_ACCOUNT_ALREADY_EXISTS);
        }

        MerchantSubAccount sub = new MerchantSubAccount();
        sub.setId(IdUtil.getSnowflakeNextId());
        sub.setMerchantId(merchantId);
        sub.setUserId(req.getUserId());
        sub.setRole(req.getRole());
        sub.setStatus(1);
        subAccountMapper.insert(sub);

        log.info("子账号添加: merchantId={}, userId={}, role={}", merchantId, req.getUserId(), req.getRole());
        return toVO(sub);
    }

    @Transactional
    public SubAccountVO updateRole(Long subAccountId, String role) {
        if (!VALID_ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.SUB_ACCOUNT_ROLE_INVALID);
        }
        MerchantSubAccount sub = findById(subAccountId);
        sub.setRole(role);
        subAccountMapper.updateById(sub);
        return toVO(sub);
    }

    @Transactional
    public SubAccountVO updateStatus(Long subAccountId, Integer status) {
        if (status != 0 && status != 1) {
            throw new BusinessException(ErrorCode.SUB_ACCOUNT_ROLE_INVALID.getCode(), "状态值非法");
        }
        MerchantSubAccount sub = findById(subAccountId);
        sub.setStatus(status);
        subAccountMapper.updateById(sub);
        log.info("子账号状态变更: id={}, status={}", subAccountId, status);
        return toVO(sub);
    }

    @Transactional
    public void remove(Long subAccountId) {
        MerchantSubAccount sub = findById(subAccountId);
        subAccountMapper.deleteById(subAccountId);
        log.info("子账号移除: id={}, userId={}", subAccountId, sub.getUserId());
    }

    private MerchantSubAccount findById(Long id) {
        MerchantSubAccount sub = subAccountMapper.selectById(id);
        if (sub == null) throw new BusinessException(ErrorCode.SUB_ACCOUNT_NOT_FOUND);
        return sub;
    }

    private SubAccountVO toVO(MerchantSubAccount s) {
        return SubAccountVO.builder()
                .id(s.getId()).merchantId(s.getMerchantId())
                .userId(s.getUserId()).role(s.getRole())
                .status(s.getStatus()).createTime(s.getCreateTime())
                .build();
    }
}
