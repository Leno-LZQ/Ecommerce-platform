package com.ecommerce.settlementservice.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.settlementservice.entity.CommissionRule;
import com.ecommerce.settlementservice.entity.SettlementBill;
import com.ecommerce.settlementservice.entity.SettlementDetail;
import com.ecommerce.settlementservice.mapper.CommissionRuleMapper;
import com.ecommerce.settlementservice.mapper.SettlementBillMapper;
import com.ecommerce.settlementservice.mapper.SettlementDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_CONFIRMED = 1;
    public static final int STATUS_PAID = 2;

    private final CommissionRuleMapper ruleMapper;
    private final SettlementBillMapper billMapper;
    private final SettlementDetailMapper detailMapper;

    // ==================== 生成账单号 ====================
    private static String generateBillNo() {
        return "BL" + DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6);
    }

    // ==================== 生成结算账单 ====================
    /**
     * 为指定商家的指定周期生成结算账单。
     * 实际生产应由定时任务调用，入参含 order_split 已完成订单列表。
     */
    @Transactional
    public SettlementBill generateBill(Long merchantId, LocalDate periodStart, LocalDate periodEnd,
                                       List<OrderSnapshot> orders) {
        // 幂等：同一周期已有草稿/已确认账单则跳过
        SettlementBill existing = billMapper.selectOne(
                new LambdaQueryWrapper<SettlementBill>()
                        .eq(SettlementBill::getMerchantId, merchantId)
                        .eq(SettlementBill::getPeriodStart, periodStart)
                        .eq(SettlementBill::getPeriodEnd, periodEnd)
                        .in(SettlementBill::getStatus, STATUS_DRAFT, STATUS_CONFIRMED));
        if (existing != null) {
            log.info("账单已存在，跳过生成: billNo={}", existing.getBillNo());
            return existing;
        }

        // 1. 拉取所有启用规则
        List<CommissionRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<CommissionRule>().eq(CommissionRule::getStatus, 1));

        // 2. 无订单则生成零账单
        if (orders == null || orders.isEmpty()) {
            SettlementBill zeroBill = createBill(merchantId, periodStart, periodEnd,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            billMapper.insert(zeroBill);
            return zeroBill;
        }

        // 3. 逐笔计算佣金，生成明细
        BigDecimal totalOrderAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        List<SettlementDetail> details = new ArrayList<>();

        for (OrderSnapshot order : orders) {
            CommissionRule rule = matchRule(rules, order.categoryId, merchantId);
            BigDecimal rate = rule.getRate();
            BigDecimal rawCommission = order.orderAmount.multiply(rate);
            // 应用上下限
            BigDecimal commission = rawCommission;
            if (rule.getMinAmount() != null && commission.compareTo(rule.getMinAmount()) < 0) {
                commission = rule.getMinAmount();
            }
            if (rule.getMaxAmount() != null && commission.compareTo(rule.getMaxAmount()) > 0) {
                commission = rule.getMaxAmount();
            }

            SettlementDetail detail = new SettlementDetail();
            detail.setSubOrderNo(order.subOrderNo);
            detail.setOrderAmount(order.orderAmount);
            detail.setDiscountAmount(order.discountAmount != null ? order.discountAmount : BigDecimal.ZERO);
            detail.setRuleId(rule.getId());
            detail.setRateSnapshot(rate);
            detail.setCommission(commission);
            details.add(detail);

            totalOrderAmount = totalOrderAmount.add(order.orderAmount);
            totalDiscount = totalDiscount.add(order.discountAmount != null ? order.discountAmount : BigDecimal.ZERO);
            totalCommission = totalCommission.add(commission);
        }

        // 4. 创建账单
        SettlementBill bill = createBill(merchantId, periodStart, periodEnd,
                totalOrderAmount, totalDiscount, totalCommission);
        billMapper.insert(bill);

        // 5. 批量写明细（回填 billId）
        for (SettlementDetail detail : details) {
            detail.setBillId(bill.getId());
            detailMapper.insert(detail);
        }

        log.info("生成结算账单: billNo={}, merchantId={}, totalOrder={}, commission={}",
                bill.getBillNo(), merchantId, totalOrderAmount, totalCommission);
        return bill;
    }

    // ==================== 按账单号结算（确认打款） ====================
    @Transactional
    public void settleBill(String billNo) {
        SettlementBill bill = billMapper.selectOne(
                new LambdaQueryWrapper<SettlementBill>().eq(SettlementBill::getBillNo, billNo));
        if (bill == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (bill.getStatus() != STATUS_CONFIRMED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }
        bill.setStatus(STATUS_PAID);
        billMapper.updateById(bill);
        log.info("结算账单已打款: billNo={}", billNo);
    }

    // ==================== 确认账单 ====================
    @Transactional
    public void confirmBill(String billNo) {
        SettlementBill bill = billMapper.selectOne(
                new LambdaQueryWrapper<SettlementBill>().eq(SettlementBill::getBillNo, billNo));
        if (bill == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (bill.getStatus() != STATUS_DRAFT) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }
        bill.setStatus(STATUS_CONFIRMED);
        billMapper.updateById(bill);
        log.info("结算账单已确认: billNo={}", billNo);
    }

    // ==================== 商家查账单列表 ====================
    public Page<SettlementBill> listBills(Long merchantId, Integer status, int page, int size) {
        LambdaQueryWrapper<SettlementBill> wrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) wrapper.eq(SettlementBill::getMerchantId, merchantId);
        if (status != null) wrapper.eq(SettlementBill::getStatus, status);
        wrapper.orderByDesc(SettlementBill::getCreateTime);
        return billMapper.selectPage(new Page<>(page, size), wrapper);
    }

    // ==================== 计数字段（SettlementClient 调用） ====================
    public Long countByMerchantAndStatus(Long merchantId, Integer status) {
        return billMapper.selectCount(
                new LambdaQueryWrapper<SettlementBill>()
                        .eq(SettlementBill::getMerchantId, merchantId)
                        .eq(SettlementBill::getStatus, status));
    }

    // ══════════════════════════════════════════
    //  辅助方法
    // ══════════════════════════════════════════

    private SettlementBill createBill(Long merchantId, LocalDate start, LocalDate end,
                                       BigDecimal orderAmount, BigDecimal discount, BigDecimal commission) {
        SettlementBill bill = new SettlementBill();
        bill.setBillNo(generateBillNo());
        bill.setMerchantId(merchantId);
        bill.setPeriodStart(start);
        bill.setPeriodEnd(end);
        bill.setTotalOrderAmount(orderAmount);
        bill.setTotalDiscount(discount);
        bill.setTotalCommission(commission);
        bill.setSettlementAmount(orderAmount.subtract(discount).subtract(commission));
        bill.setStatus(STATUS_DRAFT);
        return bill;
    }

    /** 匹配佣金规则：先精确（category + merchant），再粗略 */
    private CommissionRule matchRule(List<CommissionRule> rules, Long categoryId, Long merchantId) {
        // 精确匹配：category + merchant
        for (CommissionRule r : rules) {
            if (Objects.equals(r.getCategoryId(), categoryId) && Objects.equals(r.getMerchantId(), merchantId)) {
                return r;
            }
        }
        // 仅 category
        for (CommissionRule r : rules) {
            if (Objects.equals(r.getCategoryId(), categoryId) && r.getMerchantId() == null) {
                return r;
            }
        }
        // 全品类全商家默认规则
        for (CommissionRule r : rules) {
            if (r.getCategoryId() == null && r.getMerchantId() == null) {
                return r;
            }
        }
        // 兜底：创建一个临时的 5% 默认规则
        CommissionRule fallback = new CommissionRule();
        fallback.setId(0L);
        fallback.setRate(new BigDecimal("0.0500"));
        fallback.setMinAmount(BigDecimal.ZERO);
        fallback.setMaxAmount(null);
        return fallback;
    }

    // ══════════════════════════════════════════
    //  内嵌类：订单快照（生成账单时传入）
    // ══════════════════════════════════════════
    public static class OrderSnapshot {
        public String subOrderNo;
        public BigDecimal orderAmount;
        public BigDecimal discountAmount;
        public Long categoryId;

        public OrderSnapshot(String subOrderNo, BigDecimal orderAmount, BigDecimal discountAmount, Long categoryId) {
            this.subOrderNo = subOrderNo;
            this.orderAmount = orderAmount;
            this.discountAmount = discountAmount;
            this.categoryId = categoryId;
        }
    }
}
