package com.ecommerce.promotionservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.promotionservice.entity.CouponUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户领券记录 Mapper —— 状态机所有流转走条件更新，保证幂等
 */
@Mapper
public interface CouponUserMapper extends BaseMapper<CouponUser> {

    // ================= 查询 =================

    /**
     * 按券码查单条（唯一键）
     */
    @Select("SELECT * FROM coupon_user WHERE coupon_code = #{couponCode}")
    CouponUser selectByCouponCode(@Param("couponCode") String couponCode);

    /**
     * 按用户+状态查券列表（走 idx_user_status）
     */
    @Select("SELECT * FROM coupon_user WHERE user_id = #{userId} AND status = #{status}")
    List<CouponUser> selectByUserAndStatus(@Param("userId") Long userId,
                                           @Param("status") Integer status);

    /**
     * 统计某用户在某个模板下已领数量（领券限领校验）
     */
    @Select("SELECT COUNT(1) FROM coupon_user WHERE user_id = #{userId} AND template_id = #{templateId}")
    int countByUserAndTemplate(@Param("userId") Long userId,
                               @Param("templateId") Long templateId);

    /**
     * 查已过期但未标记的券（CouponExpireJob 定时扫描，走 idx_expire_time）
     */
    @Select("SELECT * FROM coupon_user WHERE status = 0 AND expire_time < #{now} LIMIT #{batchSize}")
    List<CouponUser> selectExpired(@Param("now") LocalDateTime now,
                                   @Param("batchSize") int batchSize);

    /**
     * 查状态为"已锁定"的券（LockTimeoutReleaseJob 扫描，分页）
     */
    @Select("SELECT * FROM coupon_user WHERE status = 1 LIMIT #{offset}, #{limit}")
    List<CouponUser> selectLocked(@Param("offset") long offset,
                                  @Param("limit") int limit);

    /**
     * 按锁定订单号查券（release/refund 用）
     */
    @Select("SELECT * FROM coupon_user WHERE lock_order_no = #{orderNo}")
    List<CouponUser> selectByLockOrderNo(@Param("orderNo") String orderNo);

    // ================= 状态机流转（条件更新，影响行数=0 即幂等/并发冲突） =================

    /**
     * 锁定：0→1
     */
    @Update("UPDATE coupon_user SET status = 1, lock_order_no = #{orderNo} " +
            "WHERE coupon_code = #{couponCode} AND status = 0")
    int lock(@Param("couponCode") String couponCode,
             @Param("orderNo") String orderNo);

    /**
     * 核销：1→2
     */
    @Update("UPDATE coupon_user SET status = 2, use_time = NOW() " +
            "WHERE coupon_code = #{couponCode} AND status = 1")
    int confirm(@Param("couponCode") String couponCode);

    /**
     * 释放：1→0
     */
    @Update("UPDATE coupon_user SET status = 0, lock_order_no = NULL " +
            "WHERE coupon_code = #{couponCode} AND status = 1")
    int release(@Param("couponCode") String couponCode);

    /**
     * 过期标记：0→3
     */
    @Update("UPDATE coupon_user SET status = 3 " +
            "WHERE id = #{id} AND status = 0")
    int markExpired(@Param("id") Long id);

    /**
     * 退还（全额退款）：1 或 2 → 4
     */
    @Update("UPDATE coupon_user SET status = 4 " +
            "WHERE coupon_code = #{couponCode} AND status IN (1, 2)")
    int markRefunded(@Param("couponCode") String couponCode);
}
