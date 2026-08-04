package com.ecommerce.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.userservice.entity.UserRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    @Insert("INSERT INTO user_role (id, user_id, role_id) VALUES (#{id}, #{userId}, #{roleId})")
    int insertUserRole(@Param("id") Long id,
                       @Param("userId") Long userId,
                       @Param("roleId") Long roleId);
}
