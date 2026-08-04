package com.ecommerce.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.userservice.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 通过 phone_hash 查重（核心：确定性哈希使唯一索引生效） */
    @Select("SELECT * FROM user WHERE phone_hash = #{phoneHash}")
    User selectByPhoneHash(@Param("phoneHash") String phoneHash);

    /** 按用户名查重 */
    @Select("SELECT COUNT(1) FROM user WHERE username = #{username}")
    int countByUsername(@Param("username") String username);

    /** 更新密码 */
    @Update("UPDATE user SET password = #{password}, update_time = NOW() WHERE id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    @Update("UPDATE user SET phone_hash = #{phoneHash}, phone_encrypted = #{phoneEncrypted}, update_time = NOW() WHERE id = #{userId}")
    int updatePhone(@Param("userId") Long userId, @Param("phoneHash") String phoneHash , @Param("phoneEncrypted") String phoneEncrypted);

    /** 查询用户角色 */
    @Select("SELECT r.name FROM role r " +
        "INNER JOIN user_role ur ON r.id = ur.role_id " +
        "WHERE ur.user_id = #{userId}")
    List<String> selectRolesByUserId(@Param("userId") Long userId);

    /** 查询用户所有权限码（四表 JOIN + DISTINCT） */
    @Select("SELECT DISTINCT p.code FROM permission p " +
        "INNER JOIN role_permission rp ON p.id = rp.permission_id " +
        "INNER JOIN user_role ur ON rp.role_id = ur.role_id " +
        "WHERE ur.user_id = #{userId}")
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);
}
