package com.ecommerce.paymentservice.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.client.OrderClient;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    // ========== 状态常量 ==========
    public static final int STATUS_PENDING = 0;      // 待支付
    public static final int STATUS_SUCCESS = 1;      // 支付成功
    public static final int STATUS_FAILED = 2;       // 支付失败
    public static final int STATUS_REFUNDED = 3;     // 已退款

    public static final int PAY_TYPE_ALIPAY = 1;
    public static final int PAY_TYPE_WECHAT = 2;

    private final PaymentMapper paymentMapper;
    private final OrderClient orderClient;

    // ==================== 生成支付单号 ====================
    private static String generatePayNo() {
        return "PAY" + DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6);
    }

    // ==================== 创建支付单 ====================
    @Transactional
    public String create(String orderNo, Long userId, BigDecimal payAmount) {
        // 幂等：同一订单号已有支付单则直接返回
        Payment existing = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (existing != null) {
            log.info("支付单已存在，返回已有 payNo: orderNo={}, payNo={}", orderNo, existing.getPayNo());
            return existing.getPayNo();
        }

        String payNo = generatePayNo();
        Payment payment = new Payment();
        payment.setPayNo(payNo);
        payment.setOrderNo(orderNo);
        payment.setUserId(userId);
        payment.setTotalAmount(payAmount);
        payment.setPayType(PAY_TYPE_ALIPAY);           // 默认支付宝
        payment.setStatus(STATUS_PENDING);
        paymentMapper.insert(payment);

        log.info("创建支付单: payNo={}, orderNo={}, amount={}", payNo, orderNo, payAmount);
        return payNo;
    }

    // ==================== 关闭支付单 ====================
    @Transactional
    public void close(String payNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getPayNo, payNo));
        if (payment == null) {
            log.warn("关闭支付单失败，支付单不存在: payNo={}", payNo);
            return;
        }
        if (payment.getStatus() != STATUS_PENDING) {
            log.warn("支付单状态不允许关闭: payNo={}, status={}", payNo, payment.getStatus());
            return;
        }
        payment.setStatus(STATUS_FAILED);
        paymentMapper.updateById(payment);
        log.info("支付单已关闭: payNo={}", payNo);
    }

    // ==================== 支付回调 ====================
    @Transactional
    public void callback(String payNo, boolean success) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getPayNo, payNo));
        if (payment == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (payment.getStatus() != STATUS_PENDING) {
            log.warn("支付单状态不允许回调: payNo={}, status={}", payNo, payment.getStatus());
            return;
        }

        payment.setStatus(success ? STATUS_SUCCESS : STATUS_FAILED);
        payment.setCallbackTime(LocalDateTime.now());
        paymentMapper.updateById(payment);

        log.info("支付回调: payNo={}, success={}", payNo, success);

        // 支付成功 → 通知 order-service 标记订单已支付（best-effort）
        if (success) {
            try {
                orderClient.markPaid(payment.getOrderNo());
                log.info("已通知订单支付成功: orderNo={}", payment.getOrderNo());
            } catch (Exception e) {
                log.error("通知订单支付失败: orderNo={}", payment.getOrderNo(), e);
            }
        }
    }

    // ==================== 按订单号查询 ====================
    public Payment getByOrderNo(String orderNo) {
        return paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
    }

    /** 今日交易额（成功支付且回调时间=今天） */
    public BigDecimal getTodayRevenue() {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime todayEnd = todayStart.plusDays(1);

        return paymentMapper.selectList(
                new LambdaQueryWrapper<Payment>()
                    .eq(Payment::getStatus, STATUS_SUCCESS)
                    .ge(Payment::getCallbackTime, todayStart)
                    .lt(Payment::getCallbackTime, todayEnd))
                .stream()
                .map(Payment::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
