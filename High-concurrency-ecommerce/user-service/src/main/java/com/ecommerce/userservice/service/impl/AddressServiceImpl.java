package com.ecommerce.userservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.userservice.dto.AddressRequest;
import com.ecommerce.userservice.entity.UserAddress;
import com.ecommerce.userservice.mapper.UserAddressMapper;
import com.ecommerce.userservice.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final UserAddressMapper addressMapper;

    private static final int MAX_ADDRESS_COUNT = 20;

    @Override
    public List<UserAddress> listByUserId(Long userId) {
        return addressMapper.selectList(
            Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getUpdateTime));
    }

    @Override
    @Transactional
    public UserAddress create(Long userId, AddressRequest request) {
        long count = addressMapper.selectCount(
            Wrappers.<UserAddress>lambdaQuery().eq(UserAddress::getUserId, userId));
        if (count >= MAX_ADDRESS_COUNT) {
            throw new BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }

        boolean isDefault = request.getIsDefault() != null && request.getIsDefault() == 1;
        if (isDefault) {
            addressMapper.clearDefault(userId);
        }

        UserAddress addr = new UserAddress();
        addr.setUserId(userId);
        addr.setReceiverName(request.getReceiverName());
        addr.setPhone(request.getPhone());
        addr.setProvince(request.getProvince());
        addr.setCity(request.getCity());
        addr.setDistrict(request.getDistrict());
        addr.setDetail(request.getDetail());
        addr.setIsDefault(isDefault ? 1 : 0);

        addressMapper.insert(addr);
        log.info("用户 {} 新增收货地址 id={}", userId, addr.getId());
        return addr;
    }

    @Override
    public void update(Long userId, Long addressId, AddressRequest request) {
        UserAddress addr = getOwnAddress(userId, addressId);

        addr.setReceiverName(request.getReceiverName());
        addr.setPhone(request.getPhone());
        addr.setProvince(request.getProvince());
        addr.setCity(request.getCity());
        addr.setDistrict(request.getDistrict());
        addr.setDetail(request.getDetail());

        addressMapper.updateById(addr);
        log.info("用户 {} 修改收货地址 id={}", userId, addressId);
    }

    @Override
    public void delete(Long userId, Long addressId) {
        UserAddress addr = getOwnAddress(userId, addressId);
        addressMapper.deleteById(addr.getId());
        log.info("用户 {} 删除收货地址 id={}", userId, addressId);
    }

    @Override
    @Transactional
    public void setDefault(Long userId, Long addressId) {
        getOwnAddress(userId, addressId);   // 校验地址存在
        addressMapper.clearDefault(userId);
        addressMapper.setDefault(addressId, userId);
        log.info("用户 {} 设置默认地址 id={}", userId, addressId);
    }

    private UserAddress getOwnAddress(Long userId, Long addressId) {
        UserAddress addr = addressMapper.selectById(addressId);
        if (addr == null || !addr.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        return addr;
    }
}
