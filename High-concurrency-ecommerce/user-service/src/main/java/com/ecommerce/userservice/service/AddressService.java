package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.AddressRequest;
import com.ecommerce.userservice.entity.UserAddress;

import java.util.List;

public interface AddressService {

    /** 获取用户所有地址 */
    List<UserAddress> listByUserId(Long userId);

    /** 新增地址 */
    UserAddress create(Long userId, AddressRequest request);

    /** 修改地址 */
    void update(Long userId, Long addressId, AddressRequest request);

    /** 删除地址 */
    void delete(Long userId, Long addressId);

    /** 设置默认地址（事务原子操作） */
    void setDefault(Long userId, Long addressId);
}
