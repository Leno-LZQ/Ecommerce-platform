package com.ecommerce.orderservice.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.client.ProductClient;
import com.ecommerce.client.InventoryClient;
import com.ecommerce.dto.product.SkuDetailDTO;
import com.ecommerce.dto.promotion.PromotionLockResponse;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.CreateOrderResponse;
import com.ecommerce.orderservice.dto.OrderPageQuery;
import com.ecommerce.orderservice.dto.OrderVO;
import com.ecommerce.orderservice.entity.OrderItem;
import com.ecommerce.orderservice.entity.OrderMessage;
import com.ecommerce.orderservice.entity.OrderSplit;
import com.ecommerce.orderservice.entity.Orders;
import com.ecommerce.orderservice.event.OrderEventPublisher;
import com.ecommerce.orderservice.mapper.OrderItemMapper;
import com.ecommerce.orderservice.mapper.OrderMessageMapper;
import com.ecommerce.orderservice.mapper.OrderSplitMapper;
import com.ecommerce.orderservice.mapper.OrdersMapper;
import com.ecommerce.orderservice.saga.OrderSagaOrchestrator;
import com.ecommerce.orderservice.saga.SagaContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    // ========== 订单状态常量 ==========
    public static final int STATUS_PENDING = 0;    // 待支付
    public static final int STATUS_PAID = 1;        // 已支付
    public static final int STATUS_SHIPPED = 2;     // 已发货
    public static final int STATUS_COMPLETED = 3;   // 已完成
    public static final int STATUS_CANCELLED = 4;   // 已取消
    public static final int STATUS_REFUNDED = 5;    // 已退款

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderSplitMapper orderSplitMapper;
    private final OrderMessageMapper orderMessageMapper;
    private final OrderEventPublisher eventPublisher;
    private final OrderSagaOrchestrator sagaOrchestrator;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    // ==================== 生成订单号 ====================
    private static String generateOrderNo() {
        return "OD" + DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6);
    }

    /** 生成子订单号 */
    private static String generateSubOrderNo() {
        return "SO" + DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6);
    }

    // ==================== 创建订单 ====================
    public CreateOrderResponse createOrder(CreateOrderRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        String orderNo = generateOrderNo();

        // 1. 构建明细快照（从 product-service 查询 SKU 详情，填充商家/商品/价格）
        Map<Long, Long> productCategoryMap = new HashMap<>();
        List<OrderItem> items = req.getItems().stream().map(dto -> {
            SkuDetailDTO sku = productClient.getSkuDetail(dto.getSkuId()).getData();
            if (sku.getProductId() != null) {
                productCategoryMap.putIfAbsent(sku.getProductId(), sku.getCategoryId());
            }
            if (sku == null) {
                throw new BusinessException(ErrorCode.SKU_NOT_FOUND);
            }
            OrderItem item = new OrderItem();
            item.setSkuId(dto.getSkuId());
            item.setProductId(sku.getProductId());
            item.setMerchantId(sku.getMerchantId());
            item.setProductName(sku.getProductName());
            item.setProductImage(sku.getImage());
            item.setQuantity(dto.getQuantity());
            item.setOrderNo(orderNo);
            item.setPrice(sku.getPrice());
            item.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));
            item.setDiscountAmount(BigDecimal.ZERO);
            return item;
        }).toList();

        // 2. 计算金额
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. 构建订单 + 本地消息
        Orders orders = new Orders();
        orders.setOrderNo(orderNo);
        orders.setUserId(userId);
        orders.setTotalAmount(totalAmount);
        orders.setPayAmount(totalAmount);           // Saga 后会从 discountAmount 修正
        orders.setStatus(STATUS_PENDING);
        orders.setConsignee(req.getConsignee());
        orders.setPhone(req.getPhone());
        orders.setAddress(req.getAddress());
        orders.setRemark(req.getRemark());
        // 3.1 按商家分组生成子订单（order_split），并回填明细 subOrderNo
        Map<Long, List<OrderItem>> merchantGroups = items.stream()
                .collect(Collectors.groupingBy(OrderItem::getMerchantId));
        orders.setIsMultiMerchant(merchantGroups.size() > 1 ? 1 : 0);
        List<OrderSplit> splits = new ArrayList<>();
        for (Map.Entry<Long, List<OrderItem>> entry : merchantGroups.entrySet()) {
            String subOrderNo = generateSubOrderNo();
            BigDecimal subTotal = entry.getValue().stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            for (OrderItem item : entry.getValue()) {
                item.setSubOrderNo(subOrderNo);
            }
            OrderSplit split = new OrderSplit();
            split.setOrderNo(orderNo);
            split.setSubOrderNo(subOrderNo);
            split.setMerchantId(entry.getKey());
            split.setTotalAmount(subTotal);
            split.setDiscountAmount(BigDecimal.ZERO);
            split.setPayAmount(subTotal);
            split.setStatus(STATUS_PENDING);
            splits.add(split);
        }
        OrderMessage message = buildMessage(orderNo, "order.created", orders);

        // 4. 先提交订单本地数据（独立事务）。
        //    原因：Saga 步骤（如 payment-service 创建支付单）有外键引用 orders(order_no)，
        //    若订单事务不先提交，支付插入会因父行被锁而阻塞，形成分布式死锁。
        persistOrder(orders, items, splits, message);

        // 5. Saga 编排（预留库存 + 锁券 + 创建支付）
        SagaContext context = new SagaContext();
        context.setOrderNo(orderNo);
        context.setUserId(userId);
        context.setItems(items);
        context.setPayAmount(totalAmount);
        // 优惠券从请求中取；商品→分类映射由步骤 1 的 SKU 详情填充
        context.setCouponCodes(req.getCouponCodes() != null ? req.getCouponCodes() : Collections.emptyList());
        context.setProductCategoryMap(productCategoryMap);
        try {
            sagaOrchestrator.execute(context);
        } catch (Exception e) {
            // Saga 失败：订单已提交，标记为已取消并删除本地消息（避免重试投递已取消订单）
            cancelOrderAfterSagaFailure(orderNo);
            throw e;
        }

        // 6. Saga 后修正实付金额 + 回写子订单/明细优惠分摊
        if (context.getDiscountAmount() != null) {
            orders.setPayAmount(totalAmount.subtract(context.getDiscountAmount()));
            ordersMapper.updateById(orders);

            if (context.getLockResponse() != null) {
                // 按商家回写 order_split.discount_amount / pay_amount
                if (context.getLockResponse().getSplits() != null) {
                    for (PromotionLockResponse.MerchantSplit ms : context.getLockResponse().getSplits()) {
                        if (ms.getMerchantId() == null || ms.getDiscountAmount() == null) continue;
                        splits.stream()
                            .filter(s -> ms.getMerchantId().equals(s.getMerchantId()))
                            .findFirst()
                            .ifPresent(s -> {
                                s.setDiscountAmount(ms.getDiscountAmount());
                                s.setPayAmount(s.getTotalAmount().subtract(ms.getDiscountAmount()));
                                orderSplitMapper.updateById(s);
                            });
                    }
                }
                // 行级回写 order_item.discount_amount
                if (context.getLockResponse().getItems() != null) {
                    for (PromotionLockResponse.ItemDiscount idc : context.getLockResponse().getItems()) {
                        if (idc.getSkuId() == null || idc.getDiscountAmount() == null) continue;
                        orderItemMapper.update(null,
                            new LambdaUpdateWrapper<OrderItem>()
                                .eq(OrderItem::getOrderNo, orderNo)
                                .eq(OrderItem::getSkuId, idc.getSkuId())
                                .set(OrderItem::getDiscountAmount, idc.getDiscountAmount()));
                    }
                }
            }
        }

        // 7. 发布订单创建事件
        eventPublisher.publishCreated(orders);

        return new CreateOrderResponse(orderNo, totalAmount, orders.getPayAmount());
    }

    /** 提交订单本地数据（订单 + 明细 + 子订单 + 本地消息表），独立事务，先于 Saga 提交 */
    @Transactional
    public void persistOrder(Orders orders, List<OrderItem> items, List<OrderSplit> splits, OrderMessage message) {
        ordersMapper.insert(orders);
        items.forEach(orderItemMapper::insert);
        splits.forEach(orderSplitMapper::insert);
        orderMessageMapper.insert(message);
    }

    /** Saga 失败补偿：订单标记为已取消，删除本地消息记录 */
    @Transactional
    public void cancelOrderAfterSagaFailure(String orderNo) {
        Orders update = new Orders();
        update.setStatus(STATUS_CANCELLED);
        ordersMapper.update(update,
            new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        // 同步子订单状态为已取消
        OrderSplit splitCancelled = new OrderSplit();
        splitCancelled.setStatus(STATUS_CANCELLED);
        orderSplitMapper.update(splitCancelled,
            new LambdaQueryWrapper<OrderSplit>().eq(OrderSplit::getOrderNo, orderNo));
        orderMessageMapper.delete(
            new LambdaQueryWrapper<OrderMessage>().eq(OrderMessage::getOrderNo, orderNo));
        log.warn("Saga 失败，订单已标记取消: orderNo={}", orderNo);
    }

    // ==================== 支付成功 ====================
    /** 支付成功后标记订单已支付（payment-service 回调通知） */
    @Transactional
    public void markPaid(String orderNo) {
        Orders orders = ordersMapper.selectOne(
            new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        if (orders == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (orders.getStatus() != STATUS_PENDING) {
            log.warn("订单状态不允许标记已支付(幂等跳过): orderNo={}, status={}", orderNo, orders.getStatus());
            return;
        }
        orders.setStatus(STATUS_PAID);
        orders.setPayTime(LocalDateTime.now());
        ordersMapper.updateById(orders);

        // 同步子订单状态为已支付（发货以子订单为准）
        OrderSplit splitPaid = new OrderSplit();
        splitPaid.setStatus(STATUS_PAID);
        orderSplitMapper.update(splitPaid,
            new LambdaQueryWrapper<OrderSplit>().eq(OrderSplit::getOrderNo, orderNo));

        // 确认库存扣减（best-effort）
        try {
            inventoryClient.confirm(Map.of("orderNo", orderNo));
        } catch (Exception e) {
            log.error("库存确认失败: orderNo={}", orderNo, e);
        }

        // 发布 order.paid 事件（下游：消息通知/促销核销等）
        Long merchantId = null;
        List<OrderItem> items = orderItemMapper.selectList(
            new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, orderNo));
        if (!items.isEmpty()) {
            merchantId = items.get(0).getMerchantId();
        }
        eventPublisher.publishPaid(orders, merchantId);
        log.info("订单已支付: orderNo={}", orderNo);
    }
    // ==================== 订单详情 ====================
    public OrderVO getOrderDetail(String orderNo) {
        Orders orders = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        if (orders == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, orderNo));
        return toVO(orders);
    }

    // ==================== 分页查询订单 ====================
    public Page<OrderVO> pageQuery(OrderPageQuery query) {
        // 从安全上下文注入买家/商家ID：商家按子订单商家维度过滤，买家按 userId 过滤
        Long merchantId = SecurityUtils.getCurrentMerchantId();
        Long userId = SecurityUtils.getCurrentUserId();

        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            List<String> orderNos = orderSplitMapper.selectList(
                    new LambdaQueryWrapper<OrderSplit>()
                        .select(OrderSplit::getOrderNo)
                        .eq(OrderSplit::getMerchantId, merchantId))
                .stream().map(OrderSplit::getOrderNo).distinct().toList();
            if (orderNos.isEmpty()) {
                return new Page<>(query.getPage() != null ? query.getPage() : 1,
                    query.getSize() != null ? query.getSize() : 10, 0);
            }
            wrapper.in(Orders::getOrderNo, orderNos);
        } else if (userId != null) {
            wrapper.eq(Orders::getUserId, userId);
        }
        if (query.getStatus() != null) {
            wrapper.eq(Orders::getStatus, query.getStatus());
        }
        if (query.getPayType() != null) {
            wrapper.eq(Orders::getPayType, query.getPayType());
        }
        if (query.getOrderNo() != null && !query.getOrderNo().isBlank()) {
            wrapper.like(Orders::getOrderNo, query.getOrderNo());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(Orders::getCreateTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(Orders::getCreateTime, query.getEndTime());
        }
        wrapper.orderByDesc(Orders::getCreateTime);

        int page = query.getPage() != null ? query.getPage() : 1;
        int size = query.getSize() != null ? query.getSize() : 10;

        Page<Orders> pageResult = ordersMapper.selectPage(new Page<>(page, size), wrapper);
        Page<OrderVO> voPage = new Page<>(page, size, pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    // ==================== 取消订单 ====================
    @Transactional
    public void cancelOrder(String orderNo, String reason) {
        Orders orders = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        if (orders == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (orders.getStatus() != STATUS_PENDING && orders.getStatus() != STATUS_PAID) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }

        orders.setStatus(STATUS_CANCELLED);
        orders.setCancelReason(reason);
        ordersMapper.updateById(orders);

        // 同步子订单状态为已取消
        OrderSplit splitCancelled = new OrderSplit();
        splitCancelled.setStatus(STATUS_CANCELLED);
        orderSplitMapper.update(splitCancelled,
            new LambdaQueryWrapper<OrderSplit>().eq(OrderSplit::getOrderNo, orderNo));

        // 写入取消事件消息
        OrderMessage message = buildMessage(orderNo, "order.cancelled", orders);
        orderMessageMapper.insert(message);

        eventPublisher.publishCancelled(orders);
    }

    // ==================== 发货（子订单维度） ====================
    @Transactional
    public void shipOrder(String subOrderNo) {
        OrderSplit split = orderSplitMapper.selectOne(
                new LambdaQueryWrapper<OrderSplit>().eq(OrderSplit::getSubOrderNo, subOrderNo));
        if (split == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (split.getStatus() != STATUS_PAID) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }

        split.setStatus(STATUS_SHIPPED);
        orderSplitMapper.updateById(split);

        eventPublisher.publishShipped(split.getOrderNo());
    }

    // ==================== 退款 ====================
    @Transactional
    public void refundOrder(String orderNo) {
        Orders orders = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        if (orders == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (orders.getStatus() != STATUS_PAID && orders.getStatus() != STATUS_SHIPPED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }

        orders.setStatus(STATUS_REFUNDED);
        ordersMapper.updateById(orders);

        // 同步子订单状态为已退款
        OrderSplit splitRefunded = new OrderSplit();
        splitRefunded.setStatus(STATUS_REFUNDED);
        orderSplitMapper.update(splitRefunded,
            new LambdaQueryWrapper<OrderSplit>().eq(OrderSplit::getOrderNo, orderNo));

        eventPublisher.publishRefunded(orderNo, orders.getPayAmount());
    }

    // ==================== 构建消息 ====================
    private OrderMessage buildMessage(String orderNo, String routingKey, Orders orders) {
        OrderMessage msg = new OrderMessage();
        msg.setOrderNo(orderNo);
        msg.setExchange("order.exchange");
        msg.setRoutingKey(routingKey);
        msg.setMessageBody("{\"orderNo\":\"" + orderNo + "\",\"status\":" + orders.getStatus() + "}");
        msg.setStatus(0);
        msg.setRetryCount(0);
        msg.setMaxRetry(3);
        return msg;
    }

    // ==================== 实体 → VO ====================
    private OrderVO toVO(Orders orders) {
        OrderVO vo = new OrderVO();
        vo.setId(orders.getId());
        vo.setOrderNo(orders.getOrderNo());
        vo.setUserId(orders.getUserId());
        vo.setTotalAmount(orders.getTotalAmount());
        vo.setPayAmount(orders.getPayAmount());
        vo.setStatus(orders.getStatus());
        vo.setIsMultiMerchant(orders.getIsMultiMerchant());
        vo.setPayType(orders.getPayType());
        vo.setPayTime(orders.getPayTime());
        vo.setConsignee(orders.getConsignee());
        vo.setPhone(orders.getPhone());
        vo.setAddress(orders.getAddress());
        vo.setRemark(orders.getRemark());
        vo.setCancelReason(orders.getCancelReason());
        return vo;
    }

    // ==================== 内部接口（Feign Client 调用） ====================

    /** 按订单号查订单（返回 common DTO，供 settlement-service Feign 调用） */
    public com.ecommerce.dto.order.OrderDTO getOrderByNo(String orderNo) {
        Orders orders = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        if (orders == null) return null;
        com.ecommerce.dto.order.OrderDTO dto = new com.ecommerce.dto.order.OrderDTO();
        dto.setOrderNo(orders.getOrderNo());
        dto.setUserId(orders.getUserId());
        dto.setStatus(orders.getStatus());
        return dto;
    }

    /** 按商家ID + 状态统计子订单数（merchantId 可选，null=全局） */
    public long countByMerchantAndStatus(Long merchantId, String status) {
        LambdaQueryWrapper<OrderSplit> wrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            wrapper.eq(OrderSplit::getMerchantId, merchantId);
        }
        if (status != null) {
            wrapper.eq(OrderSplit::getStatus, parseStatus(status));
        }
        return orderSplitMapper.selectCount(wrapper);
    }

    /** 主订单维度全局统计（按状态，status 可选，null=全部） */
    public long countByStatus(Integer status) {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Orders::getDeleted, 0);
        if (status != null) {
            wrapper.eq(Orders::getStatus, status);
        }
        return ordersMapper.selectCount(wrapper);
    }

    /** 状态字符串 → 整数 */
    private int parseStatus(String status) {
        return switch (status.toUpperCase()) {
            case "PENDING" -> STATUS_PENDING;
            case "PAID" -> STATUS_PAID;
            case "SHIPPED" -> STATUS_SHIPPED;
            case "COMPLETED" -> STATUS_COMPLETED;
            case "CANCELLED" -> STATUS_CANCELLED;
            case "REFUNDED" -> STATUS_REFUNDED;
            default -> throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        };
    }
}
