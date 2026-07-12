# 阶段 4：购物车 + 订单核心链路实施方案

> **目标：** Redis 购物车、下单流程（幂等/库存预扣/状态机）、RabbitMQ 延迟队列、MQ 分布式事务。  
> **预计工时：** 25~35 小时（本阶段最重，建议拆成 1.5 周）  
> **前置：** 阶段 3 商品模块完成  

---

## 4.1 模块划分

本阶段涉及三个服务，按开发顺序：

```
1. cart-service    → 购物车（Redis Hash 实现，约 4 小时）
2. order-service   → 订单核心（下单+状态机+延迟队列+分布式事务，约 18 小时）
3. inventory-service → 库存扣减（复用阶段3的Lua脚本，约 3 小时）
```

---

## 4.2 购物车服务（cart-service）

### 目录结构

```
cart-service/
└── src/main/java/com/ecommerce/cart/
    ├── CartApplication.java
    ├── controller/
    │   └── CartController.java
    ├── service/
    │   ├── CartService.java
    │   └── impl/CartServiceImpl.java
    ├── dto/
    │   ├── CartItemDTO.java
    │   ├── CartItemVO.java
    │   └── AddCartRequest.java
    └── config/
        └── RedisConfig.java
```

### 购物车核心逻辑

```java
// service/impl/CartServiceImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final StringRedisTemplate redisTemplate;
    private static final String CART_PREFIX = "cart:user:";

    // ====== 添加商品到购物车 ======
    @Override
    public void addItem(Long userId, Long productId, Long skuId, int quantity) {
        String cartKey = CART_PREFIX + userId;
        String itemKey = productId + ":" + (skuId != null ? skuId : 0);

        // 读取已有项
        Object existingObj = redisTemplate.opsForHash().get(cartKey, itemKey);
        CartItemDTO item;
        if (existingObj != null) {
            item = JSONUtil.toBean(existingObj.toString(), CartItemDTO.class);
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            item = new CartItemDTO();
            item.setProductId(productId);
            item.setSkuId(skuId);
            item.setQuantity(quantity);
            item.setSelected(true);
            item.setAddTime(System.currentTimeMillis());
        }

        redisTemplate.opsForHash().put(cartKey, itemKey, JSONUtil.toJsonStr(item));
        log.info("购物车添加: userId={}, productId={}, qty={}", userId, productId, quantity);
    }

    // ====== 获取购物车列表 ======
    @Override
    public List<CartItemVO> getCart(Long userId) {
        String cartKey = CART_PREFIX + userId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey);

        if (entries.isEmpty()) return Collections.emptyList();

        // 这里应该填充商品信息（名字、价格、图片）
        // 简化实现：通过 product-service 的 Feign 调用获取
        return entries.values().stream()
                .map(v -> JSONUtil.toBean(v.toString(), CartItemDTO.class))
                .map(this::toVO)
                .sorted((a, b) -> Long.compare(b.getAddTime(), a.getAddTime()))
                .collect(Collectors.toList());
    }

    // ====== 更新数量 ======
    @Override
    public void updateQuantity(Long userId, Long productId, Long skuId, int quantity) {
        String cartKey = CART_PREFIX + userId;
        String itemKey = productId + ":" + (skuId != null ? skuId : 0);

        if (quantity <= 0) {
            redisTemplate.opsForHash().delete(cartKey, itemKey);
        } else {
            Object existing = redisTemplate.opsForHash().get(cartKey, itemKey);
            if (existing != null) {
                CartItemDTO item = JSONUtil.toBean(existing.toString(), CartItemDTO.class);
                item.setQuantity(quantity);
                redisTemplate.opsForHash().put(cartKey, itemKey, JSONUtil.toJsonStr(item));
            }
        }
    }

    // ====== 选中/取消选中 ======
    @Override
    public void toggleSelect(Long userId, Long productId, Long skuId, boolean selected) {
        String cartKey = CART_PREFIX + userId;
        String itemKey = productId + ":" + (skuId != null ? skuId : 0);

        Object existing = redisTemplate.opsForHash().get(cartKey, itemKey);
        if (existing != null) {
            CartItemDTO item = JSONUtil.toBean(existing.toString(), CartItemDTO.class);
            item.setSelected(selected);
            redisTemplate.opsForHash().put(cartKey, itemKey, JSONUtil.toJsonStr(item));
        }
    }

    // ====== 清空已下单商品 ======
    @Override
    public void removeOrderedItems(Long userId, List<OrderItemRequest> items) {
        String cartKey = CART_PREFIX + userId;
        items.forEach(item -> {
            String itemKey = item.getProductId() + ":" + item.getSkuId();
            redisTemplate.opsForHash().delete(cartKey, itemKey);
        });
    }

    // ====== 合并购物车（游客→登录） ======
    @Override
    public void merge(Long userId, List<CartItemDTO> tempItems) {
        tempItems.forEach(item ->
            addItem(userId, item.getProductId(), item.getSkuId(), item.getQuantity()));
    }
}
```

---

## 4.3 订单服务（order-service）—— 核心

### 目录结构

```
order-service/
└── src/main/java/com/ecommerce/order/
    ├── OrderApplication.java
    ├── controller/
    │   └── OrderController.java
    ├── service/
    │   ├── OrderService.java
    │   └── impl/OrderServiceImpl.java
    ├── mapper/
    │   ├── OrderMapper.java
    │   ├── OrderItemMapper.java
    │   └── OrderMessageMapper.java
    ├── entity/
    │   ├── Orders.java
    │   ├── OrderItem.java
    │   └── OrderMessage.java
    ├── dto/
    │   ├── CreateOrderRequest.java
    │   ├── OrderItemRequest.java
    │   └── OrderVO.java
    ├── consumer/
    │   └── OrderDelayConsumer.java       # MQ 消费者
    └── mq/
        └── RabbitMQConfig.java           # MQ 配置
```

### RabbitMQ 配置（交换机+队列+绑定）

```java
// mq/RabbitMQConfig.java
package com.ecommerce.order.mq;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ==================== 订单延迟队列 ====================
    // 延迟交换机
    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange("exchange.order.delay");
    }

    // 延迟队列（消息 30 分钟后过期进入死信）
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable("queue.order.delay")
                .withArgument("x-dead-letter-exchange", "exchange.order.dlx")
                .withArgument("x-dead-letter-routing-key", "routing.order.dlx")
                .withArgument("x-message-ttl", 30 * 60 * 1000)  // 30 分钟
                .build();
    }

    // 死信交换机
    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange("exchange.order.dlx");
    }

    // 死信队列（消费者从此获取超时消息）
    @Bean
    public Queue orderDlxQueue() {
        return QueueBuilder.durable("queue.order.dlx").build();
    }

    // 绑定关系
    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDelayExchange()).with("routing.order.delay");
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(orderDlxQueue())
                .to(orderDlxExchange()).with("routing.order.dlx");
    }

    // ==================== 库存同步队列 ====================
    @Bean
    public Queue stockUpdateQueue() {
        return QueueBuilder.durable("queue.stock.update").build();
    }

    @Bean
    public DirectExchange stockExchange() {
        return new DirectExchange("exchange.stock");
    }

    @Bean
    public Binding stockBinding() {
        return BindingBuilder.bind(stockUpdateQueue())
                .to(stockExchange()).with("routing.stock.update");
    }
}
```

### 下单核心流程

```java
// service/impl/OrderServiceImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderMessageMapper orderMessageMapper;
    private final RestTemplate restTemplate;   // 调用商品服务扣库存
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final RedissonClient redissonClient;

    // ====== 下单主流程 ======
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(CreateOrderRequest request, Long userId) {
        // 1. 幂等性校验
        checkIdempotent(userId, request.getIdempotentToken());

        // 2. 获取分布式锁（同一用户同时只能有一个下单请求）
        String lockKey = "lock:order:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(5, 15, TimeUnit.SECONDS)) {
                throw new BusinessException(429, "操作太频繁，请稍后重试");
            }

            // 3. 库存预扣（调用库存服务）
            List<OrderItemRequest> items = request.getItems();
            for (OrderItemRequest item : items) {
                // HTTP 调用 inventory-service 扣库存
                String url = "http://localhost:8086/api/inventory/decrement";
                Map<String, Object> body = Map.of(
                    "skuId", item.getSkuId(),
                    "quantity", item.getQuantity()
                );
                ResponseEntity<Result<Boolean>> resp = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body), new ParameterizedTypeReference<>() {});
                if (resp.getBody() == null || !resp.getBody().getData()) {
                    throw new BusinessException(400, "商品库存不足: skuId=" + item.getSkuId());
                }
            }

            // 4. 生成订单号
            String orderNo = generateOrderNo(userId);

            try {
                // 5. 保存订单
                Orders order = buildOrder(userId, orderNo, request);
                orderMapper.insert(order);

                // 6. 保存订单明细
                List<OrderItem> orderItems = buildOrderItems(orderNo, items);
                for (OrderItem oi : orderItems) {
                    orderItemMapper.insert(oi);
                }

                // 7. 发送延迟消息（30 分钟后检查支付状态）
                rabbitTemplate.convertAndSend(
                    "exchange.order.delay", "routing.order.delay", orderNo);

                log.info("下单成功: orderNo={}, userId={}", orderNo, userId);
                return toVO(order, orderItems);

            } catch (Exception e) {
                log.error("下单失败回滚: orderNo={}", orderNo, e);
                // 回滚库存
                for (OrderItemRequest item : items) {
                    String rollbackUrl = "http://localhost:8086/api/inventory/rollback";
                    Map<String, Object> body = Map.of(
                        "skuId", item.getSkuId(), "quantity", item.getQuantity());
                    restTemplate.postForEntity(rollbackUrl, body, void.class);
                }
                throw new BusinessException(500, "下单失败，请重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "系统繁忙");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ====== 幂等性：Redis Token 机制 ======
    private void checkIdempotent(Long userId, String token) {
        String key = "idempotent:order:" + userId + ":" + token;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", 30, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(success)) {
            throw new BusinessException(400, "请勿重复提交订单");
        }
    }

    // ====== 订单号生成 ======
    private String generateOrderNo(Long userId) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String userIdSuffix = String.format("%04d", userId % 10000);
        long snowflake = IdUtil.getSnowflake().nextId();
        return date + userIdSuffix + (snowflake % 10000000);
    }

    private Orders buildOrder(Long userId, String orderNo, CreateOrderRequest req) {
        Orders order = new Orders();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(req.getTotalAmount());
        order.setPayAmount(req.getPayAmount());
        order.setStatus(0);  // 待支付
        order.setConsignee(req.getConsignee());
        order.setPhone(req.getPhone());
        order.setAddress(req.getAddress());
        return order;
    }
}
```

### 延迟队列消费者

```java
// consumer/OrderDelayConsumer.java
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayConsumer {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RestTemplate restTemplate;

    @RabbitListener(queues = "queue.order.dlx")
    public void handleOrderTimeout(String orderNo) {
        log.info("收到延迟消息: orderNo={}", orderNo);

        Orders order = orderMapper.selectOne(
                new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        if (order == null) return;

        // 只有待支付订单才自动取消
        if (order.getStatus() != 0) {
            log.info("订单已处理，跳过: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }

        // 取消订单
        order.setStatus(5);  // 已取消
        order.setCancelReason("超时未支付，系统自动取消");
        orderMapper.updateById(order);

        // 回滚库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, orderNo));
        for (OrderItem item : items) {
            try {
                String url = "http://localhost:8086/api/inventory/rollback";
                Map<String, Object> body = Map.of(
                    "skuId", item.getSkuId(), "quantity", item.getQuantity());
                restTemplate.postForEntity(url, body, void.class);
            } catch (Exception e) {
                log.error("库存回滚失败: skuId={}", item.getSkuId(), e);
                // 写入死信重试
            }
        }

        log.info("订单超时取消完成: orderNo={}", orderNo);
    }
}
```

### 订单状态机

```java
// entity/OrderStatus.java
public enum OrderStatus {
    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    SHIPPED(2, "已发货"),
    RECEIVED(3, "已收货"),
    COMPLETED(4, "已完成"),
    CANCELLED(5, "已取消"),
    REFUNDED(6, "已退款");

    private final int code;
    private final String desc;

    // 合法的状态转换
    static {
        addTransition(PENDING, PAID, CANCELLED);
        addTransition(PAID, SHIPPED, REFUNDED);
        addTransition(SHIPPED, RECEIVED);
        addTransition(RECEIVED, COMPLETED);
    }

    private static final Map<Integer, Set<Integer>> STATE_MACHINE = new HashMap<>();

    private static void addTransition(OrderStatus from, OrderStatus... toList) {
        STATE_MACHINE.put(from.code,
            Arrays.stream(toList).map(s -> s.code).collect(Collectors.toSet()));
    }

    public static boolean canTransition(int fromCode, int toCode) {
        Set<Integer> allowed = STATE_MACHINE.get(fromCode);
        return allowed != null && allowed.contains(toCode);
    }
}
```

### 修改订单状态的公共方法

```java
// service/OrderServiceImpl.java 追加
public void updateStatus(String orderNo, int newStatus) {
    Orders order = orderMapper.selectOne(
        new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
    if (order == null) throw new BusinessException("订单不存在");

    if (!OrderStatus.canTransition(order.getStatus(), newStatus)) {
        throw new BusinessException("订单状态转换非法: "
            + order.getStatus() + " → " + newStatus);
    }

    order.setStatus(newStatus);
    orderMapper.updateById(order);
}
```

---

## 4.4 分布式事务：本地消息表

### 下单时写入消息表

```java
// 在下单流程中增加：
OrderMessage msg = new OrderMessage();
msg.setOrderNo(orderNo);
msg.setMessageBody(JSONUtil.toJsonStr(order));
msg.setExchange("exchange.order.event");
msg.setRoutingKey("routing.order.created");
msg.setStatus(0);
orderMessageMapper.insert(msg);
```

### 定时任务重发

```java
// task/MessageRetryTask.java
@Component
@Slf4j
@RequiredArgsConstructor
public class MessageRetryTask {

    private final OrderMessageMapper messageMapper;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 10_000)  // 每 10 秒
    public void retryPending() {
        List<OrderMessage> pending = messageMapper.selectList(
            new LambdaQueryWrapper<OrderMessage>()
                .eq(OrderMessage::getStatus, 0)
                .lt(OrderMessage::getRetryCount, OrderMessage::getMaxRetry)
                .last("LIMIT 50"));

        for (OrderMessage msg : pending) {
            try {
                rabbitTemplate.convertAndSend(
                    msg.getExchange(), msg.getRoutingKey(), msg.getMessageBody());
                msg.setStatus(1);
                log.info("消息重发成功: orderNo={}", msg.getOrderNo());
            } catch (Exception e) {
                msg.setRetryCount(msg.getRetryCount() + 1);
                if (msg.getRetryCount() >= msg.getMaxRetry()) {
                    msg.setStatus(3);
                    log.error("消息重发达上限: orderNo={}", msg.getOrderNo());
                }
            }
            messageMapper.updateById(msg);
        }
    }
}
```

---

## 4.5 Controller 层

```java
// controller/OrderController.java
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 下单
    @PostMapping
    public Result<OrderVO> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        Long userId = getCurrentUserId();  // 从 JWT 获取
        OrderVO result = orderService.createOrder(req, userId);
        return Result.success(result);
    }

    // 查询订单列表
    @GetMapping
    public Result<IPage<OrderVO>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = getCurrentUserId();
        return Result.success(orderService.pageOrders(userId, page, size));
    }

    // 订单详情
    @GetMapping("/{orderNo}")
    public Result<OrderVO> getOrder(@PathVariable String orderNo) {
        return Result.success(orderService.getOrder(orderNo));
    }

    // 取消订单
    @PutMapping("/{orderNo}/cancel")
    public Result<Void> cancelOrder(@PathVariable String orderNo) {
        Long userId = getCurrentUserId();
        orderService.cancelOrder(orderNo, userId);
        return Result.success();
    }
}
```

---

## 4.6 库存服务补充（inventory-service）

```java
// controller/InventoryController.java（供 order-service 内部调用）
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/decrement")
    public Result<Boolean> decrement(@RequestBody StockRequest req) {
        return Result.success(inventoryService.decrement(req.getSkuId(), req.getQuantity()));
    }

    @PostMapping("/rollback")
    public Result<Void> rollback(@RequestBody StockRequest req) {
        inventoryService.rollback(req.getSkuId(), req.getQuantity());
        return Result.success();
    }
}
```

---

## 4.7 验证测试

```bash
# 1. 添加商品到购物车
curl -X POST http://localhost:8083/api/cart/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"productId":1,"skuId":1,"quantity":2}'

# 2. 查看购物车
curl http://localhost:8083/api/cart -H "Authorization: Bearer <token>"

# 3. 获取幂等 Token
curl http://localhost:8084/api/orders/idempotent-token \
  -H "Authorization: Bearer <token>"

# 4. 下单
curl -X POST http://localhost:8084/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "idempotentToken":"xxx",
    "items":[{"productId":1,"skuId":1,"quantity":1,"price":7999}],
    "totalAmount":7999,"payAmount":7999,
    "consignee":"张三","phone":"13800138000","address":"北京市朝阳区"
  }'

# 5. 幂等性测试（相同 Token 再次提交）
curl -X POST http://localhost:8084/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"idempotentToken":"xxx",...}'
# 预期：返回 "请勿重复提交订单"

# 6. 查看订单
curl http://localhost:8084/api/orders/2024071012345678 \
  -H "Authorization: Bearer <token>"

# 7. 取消订单
curl -X PUT http://localhost:8084/api/orders/2024071012345678/cancel \
  -H "Authorization: Bearer <token>"
```

---

## 4.8 下单价格安全校验（防止前端篡改）

> **设计原则**：永远不信任前端传来的价格。下单时以后端数据库实时价格为准。

```java
// OrderServiceImpl.createOrder() 中下单前增加：
// ⚡ 价格安全校验
private void validateOrderPrice(CreateOrderRequest request) {
    BigDecimal computedTotal = BigDecimal.ZERO;
    for (OrderItemRequest item : request.getItems()) {
        // 从 DB 查询真实价格（不信任前端传来的 price）
        ProductSku sku = skuMapper.selectById(item.getSkuId());
        if (sku == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        // 强制覆盖为数据库真实价格
        item.setPrice(sku.getPrice());
        computedTotal = computedTotal.add(
            sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }

    // 校验前端传来的总金额是否与后端计算一致（误差 < 0.01 元）
    if (request.getTotalAmount().subtract(computedTotal).abs()
            .compareTo(new BigDecimal("0.01")) > 0) {
        throw new BusinessException(ErrorCode.ORDER_PRICE_MISMATCH);
    }

    // 强制覆盖为后端计算值
    request.setTotalAmount(computedTotal);
    request.setPayAmount(computedTotal);
}
```

---

## 4.9 下单流程升级（库存预占/实扣模式）

> 替换原先"直接扣减 → 失败回滚"为"预占 → 支付确认实扣 → 超时释放"三步骤。

```java
// ⚡ 下单时改为调用 inventory-service 的预占接口
for (OrderItemRequest item : items) {
    // HTTP 调用 inventory-service 预占库存
    String url = "http://localhost:8086/api/inventory/freeze";
    Map<String, Object> body = Map.of(
        "skuId", item.getSkuId(),
        "quantity", item.getQuantity()
    );
    ResponseEntity<Result<Boolean>> resp = restTemplate.exchange(
        url, HttpMethod.POST,
        new HttpEntity<>(body), new ParameterizedTypeReference<>() {});
    if (resp.getBody() == null || !resp.getBody().getData()) {
        // 回滚已预占的 SKU
        rollbackFrozenStock(items.subList(0, processedIndex));
        throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
    }
    processedIndex++;
}
```

**支付成功时实扣**（在 `PaymentServiceImpl.handlePaySuccess` 中调用）：

```java
// payment-service 支付成功 → 调用 inventory-service 实扣
restTemplate.postForEntity(
    "http://localhost:8086/api/inventory/confirm", 
    Map.of("skuId", skuId, "quantity", quantity), 
    void.class);
```

**超时取消时释放**（在 `OrderDelayConsumer.handleOrderTimeout` 中调用）：

```java
// 释放冻结库存（replaces 原先的 rollback）
restTemplate.postForEntity(
    "http://localhost:8086/api/inventory/release",
    Map.of("skuId", skuId, "quantity", quantity),
    void.class);
```

---

## 4.10 秒杀下单流程（异步削峰）

> 秒杀与普通下单的核心区别：**先过限流 → Lua 原子扣 → MQ 异步生成订单**，用户无需等待订单生成。

```java
// order-service 新增 SeckillController
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillOrderProducer seckillProducer;

    /**
     * 秒杀下单 → 先 Redis 扣库存，再 MQ 异步生成订单
     * 用户立即获得"排队中"的响应，不阻塞
     */
    @PostMapping("/{seckillId}")
    @SentinelResource(value = "seckillOrder", fallback = "seckillBlockHandler")
    public Result<String> seckillOrder(@PathVariable Long seckillId,
                                        @RequestParam Long skuId,
                                        @RequestHeader("Authorization") String token) {
        Long userId = getCurrentUserId();

        // 1. Redis Lua 原子扣减（布隆已在 product-service 侧拦截）
        Long result = seckillCacheService.seckillDecrement(seckillId, skuId, userId);
        return switch (result.intValue()) {
            case 1 -> {
                // 2. 发送 MQ 异步生成订单（削峰填谷）
                seckillProducer.sendOrderMessage(seckillId, skuId, userId);
                yield Result.success("抢购成功，订单生成中...");
            }
            case 0 -> Result.error(ErrorCode.STOCK_INSUFFICIENT);
            case -1 -> Result.error(400, "您已参与过本次秒杀");
            default -> Result.error(ErrorCode.SYSTEM_ERROR);
        };
    }

    // Sentinel 限流降级
    public Result<String> seckillBlockHandler(Long seckillId, Long skuId,
                                               String token, BlockException e) {
        return Result.error(ErrorCode.RATE_LIMITED);
    }
}
```

```java
// mq/SeckillOrderProducer.java
@Component
@RequiredArgsConstructor
@Slf4j
public class SeckillOrderProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendOrderMessage(Long seckillId, Long skuId, Long userId) {
        SeckillOrderMessage msg = new SeckillOrderMessage(seckillId, skuId, userId);
        rabbitTemplate.convertAndSend("exchange.seckill", "routing.seckill.order", msg);
        log.info("秒杀订单消息已发送: seckillId={}, userId={}", seckillId, userId);
    }
}

// MQ 消费者异步生成真实订单
@RabbitListener(queues = "queue.seckill.order")
public void handleSeckillOrder(SeckillOrderMessage msg) {
    // 1. 查询秒杀价格
    // 2. 调用普通下单逻辑（幂等 + 价格校验）
    // ⚡ 3. 订单生成后 → Redis Pub/Sub 推送 WebSocket 通知
    String orderNo = createSeckillOrder(msg);
    String notifyMsg = JSONUtil.toJsonStr(Map.of(
        "type", "ORDER_CREATED",
        "title", "订单已生成",
        "body", "恭喜！秒杀成功，订单号：" + orderNo,
        "orderNo", orderNo,
        "timestamp", System.currentTimeMillis()
    ));
    redisTemplate.convertAndSend("notify:user:" + msg.getUserId(), notifyMsg);
}
```

**WebSocket 推送效果**：用户秒杀后 3 秒内浏览器弹窗 → "恭喜！秒杀成功，订单号：xxx，去支付" → 点击跳转支付页面。

---

## 4.11 我的订单查询（新增——业务闭环）

```java
// OrderController.java 追加
/**
 * 用户端：我的订单列表（分页 + 状态筛选）
 */
@GetMapping("/my")
public Result<IPage<OrderVO>> myOrders(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) Integer status) {
    Long userId = getCurrentUserId();
    LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
            .eq(Orders::getUserId, userId)
            .eq(Orders::getDeleted, 0);
    if (status != null) {
        wrapper.eq(Orders::getStatus, status);
    }
    wrapper.orderByDesc(Orders::getCreateTime);
    IPage<Orders> orderPage = orderMapper.selectPage(
        new Page<>(page, size), wrapper);
    return Result.success(orderPage.convert(this::toVO));
}

/**
 * 确认收货
 */
@PutMapping("/{orderNo}/receive")
public Result<Void> confirmReceive(@PathVariable String orderNo) {
    orderService.confirmReceive(orderNo, getCurrentUserId());
    return Result.success();
}
```

```java
// OrderServiceImpl.java 追加
public void confirmReceive(String orderNo, Long userId) {
    Orders order = orderMapper.selectOne(
        new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
    if (order == null || !order.getUserId().equals(userId)) {
        throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
    }
    updateStatus(orderNo, 3);  // 待收货 → 已收货
    updateStatus(orderNo, 4);  // 已收货 → 已完成（自动）
    log.info("订单确认收货: orderNo={}, userId={}", orderNo, userId);
}
```

---

## 4.12 检查点

- [ ] 购物车添加/删除/修改/选中功能正常
- [ ] Redis 中购物车数据正确存储
- [ ] 下单时价格以后端数据库为准，前端篡改价格被拦截
- [ ] 下单幂等 Token 机制防重成功
- [ ] 库存预占/实扣/释放三步骤原子性正常
- [ ] 下单失败已预占的库存正确释放
- [ ] 支付成功后库存实扣正确
- [ ] 订单号生成唯一无冲突
- [ ] RabbitMQ 延迟队列 30 分钟后正确取消订单并释放冻结库存
- [ ] 死信队列消息正确消费
- [ ] 本地消息表定时重发机制
- [ ] 秒杀下单 → MQ 异步生成订单 → WebSocket 实时推送
- [ ] 我的订单列表查询 + 状态筛选正常
- [ ] 确认收货 → 订单状态 3→4 正常
- [ ] **集成测试：订单幂等性 + 库存预占回滚 + 延迟队列取消**（新增）

---

## 4.13 生产级增强：订单审计日志（新增）

每次订单状态变更时记录审计日志：

```java
@Slf4j
@Component
public class OrderAuditLogger {

    public void logStatusChange(String orderNo, Integer fromStatus, Integer toStatus, 
                                 String operator, String remark) {
        log.info("ORDER_AUDIT: orderNo={}, status={}→{}, operator={}, remark={}",
            orderNo, fromStatus, toStatus, operator, remark);
        // 生产环境可改为写入审计表或发送到审计 MQ
    }
}
```

---

## 4.14 下一步

订单核心链路完成 → **[阶段 5：支付 + 搜索 + 管理后台 + 用户端](./phase-5-支付与前端.md)**
