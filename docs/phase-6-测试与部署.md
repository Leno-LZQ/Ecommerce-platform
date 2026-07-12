# 阶段 6：全量测试 + 可观测性 + CI/CD + 部署实施方案

> **目标：** 四层测试体系全覆盖，ELK + Prometheus + Grafana 可观测性搭建，GitHub Actions 完整 CI/CD 流水线带质量门禁，Docker 蓝绿部署。  
> **预计工时：** 25~30 小时  
> **前置：** 阶段 5 全部完成  

---

## 6.1 单元测试（JUnit 5 + Mockito）

### 测试框架配置

在 `build.gradle.kts` 中已包含 JUnit 5，确认依赖完整：

```kotlin
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("com.h2database:h2")  // 测试用内存数据库
}
```

### 订单服务测试（核心业务）

```java
// order-service/src/test/java/com/ecommerce/order/OrderServiceTest.java
package com.ecommerce.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.dto.OrderVO;
import com.ecommerce.order.entity.Orders;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    private static Long testUserId = 1L;

    // ========== TC-01：正常下单 ==========
    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("正常下单流程测试")
    void testCreateOrder_Success() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setIdempotentToken("test-token-001");
        req.setItems(List.of(new OrderItemRequest(1L, 1L, 1, new BigDecimal("7999.00"))));
        req.setTotalAmount(new BigDecimal("7999.00"));
        req.setPayAmount(new BigDecimal("7999.00"));
        req.setConsignee("测试用户");
        req.setPhone("13800138000");
        req.setAddress("测试地址");

        OrderVO order = orderService.createOrder(req, testUserId);

        assertNotNull(order, "订单不应为空");
        assertNotNull(order.getOrderNo(), "订单号不应为空");
        assertEquals(0, order.getStatus(), "初始状态应为待支付");
        assertEquals(new BigDecimal("7999.00"), order.getPayAmount());
        System.out.println("✅ TC-01 通过：orderNo = " + order.getOrderNo());
    }

    // ========== TC-02：库存不足下单 ==========
    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("库存不足时下单应失败")
    void testCreateOrder_InsufficientStock() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setIdempotentToken("test-token-002");
        req.setItems(List.of(new OrderItemRequest(1L, 999L, 99999, new BigDecimal("100.00"))));
        req.setTotalAmount(new BigDecimal("9999900.00"));
        req.setPayAmount(new BigDecimal("9999900.00"));

        assertThrows(BusinessException.class, () -> {
            orderService.createOrder(req, testUserId);
        }, "库存不足应抛出 BusinessException");
        System.out.println("✅ TC-02 通过：库存不足正确拦截");
    }

    // ========== TC-03：幂等性验证 ==========
    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("幂等性——重复Token提交应拒绝")
    void testCreateOrder_Idempotent() {
        String token = "test-token-duplicate";
        CreateOrderRequest req = createValidRequest(token);

        // 第一次提交应成功
        OrderVO first = orderService.createOrder(req, testUserId);
        assertNotNull(first, "第一次提交应成功");

        // 第二次提交应失败
        assertThrows(BusinessException.class, () -> {
            orderService.createOrder(req, testUserId);
        }, "重复提交应拒绝");
        System.out.println("✅ TC-03 通过：幂等性正确");
    }

    // ========== TC-04：并发下单防超卖 ==========
    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("并发下单——无超卖")
    void testCreateOrder_Concurrent_NoOversell() throws Exception {
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            executor.submit(() -> {
                try {
                    CreateOrderRequest req = createValidRequest("concurrent-" + idx);
                    OrderVO order = orderService.createOrder(req, testUserId + idx);
                    if (order != null) successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        System.out.printf("✅ TC-04：并发50线程，成功=%d，失败=%d（预期成功≤库存总量）%n",
                successCount.get(), failCount.get());
    }

    // ========== TC-05：订单状态转移 ==========
    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("订单状态机——合法/非法转换")
    void testOrderStateMachine() {
        // 先创建一个订单
        CreateOrderRequest req = createValidRequest("token-state-test");
        OrderVO order = orderService.createOrder(req, testUserId);

        // 合法转换：待支付 → 已取消
        Orders dbOrder = orderMapper.selectOne(
            new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, order.getOrderNo()));
        dbOrder.setStatus(5);  // cancelled
        dbOrder.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(dbOrder);
        System.out.println("✅ TC-05 通过：状态转换正确");
    }

    private CreateOrderRequest createValidRequest(String token) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setIdempotentToken(token);
        req.setItems(List.of(new OrderItemRequest(1L, 1L, 1, new BigDecimal("7999.00"))));
        req.setTotalAmount(new BigDecimal("7999.00"));
        req.setPayAmount(new BigDecimal("7999.00"));
        req.setConsignee("测试");
        req.setPhone("13800138000");
        req.setAddress("测试地址");
        return req;
    }
}
```

### 缓存服务测试

```java
// product-service/src/test/java/.../ProductCacheServiceTest.java
@SpringBootTest
class ProductCacheServiceTest {

    @Autowired
    private ProductCacheService cacheService;

    @Autowired
    private ProductService productService;

    @Test
    @DisplayName("缓存穿透——布隆过滤器拦截不存在ID")
    void testCachePenetration_Blocked() {
        Long notExistId = 999999L;
        // 首次查询应返回 null（布隆拦截）
        ProductVO vo = cacheService.getProductDetail(notExistId);
        assertNull(vo, "不存在的商品应返回 null");

        // 验证没有查数据库（通过日志判断）
        System.out.println("✅ 布隆过滤器拦截测试通过");
    }

    @Test
    @DisplayName("缓存命中——第二次查询走 Redis")
    void testCacheHit() {
        Long productId = 1L;
        // 预热缓存
        cacheService.getProductDetail(productId);

        long start = System.nanoTime();
        ProductVO vo = cacheService.getProductDetail(productId);
        long elapsed = System.nanoTime() - start;

        assertNotNull(vo);
        assertTrue(elapsed < 50_000_000, "缓存命中应 < 50ms");  // 50ms in nanos
        System.out.printf("✅ 缓存命中测试通过：耗时 %.2fms%n", elapsed / 1_000_000.0);
    }
}
```

### 运行测试

```bash
# 全量测试
./gradlew test

# 单独运行某模块测试
./gradlew :order-service:test

# 查看测试报告
# 打开：order-service/build/reports/tests/test/index.html
```

---

## 6.2 JMeter 压力测试

### 测试方案

| 编号 | 场景 | 并发数 | 持续时间 | 目标 TPS | 验证项 |
|:----:|:-----|:------:|:--------:|:--------:|:------|
| P1 | 商品详情（缓存命中） | 200 | 5 min | > 2000 | 缓存防护效果 |
| P2 | 商品详情（缓存失效） | 50 | 3 min | > 100 | DB 直查没有雪崩 |
| P3 | 用户登录 | 200 | 3 min | > 500 | JWT 生成性能 |
| P4 | 下单流程 | 100 | 5 min | > 80 | 幂等/锁/库存预占 |
| P5 | 混合场景 | 500 | 10 min | - | 系统综合表现 |
| P6 | **秒杀场景**（新增） | 500 | 3 min | > 3000 | Sentinel 限流 + Lua 原子扣 |

### P4：下单流程 JMeter 脚本

**Thread Group 配置：**
```
Number of Threads: 100
Ramp-up Period: 5 秒
Loop Count: Forever
Duration: 300 秒
```

**HTTP Request 配置：**
```
Method: POST
Path: /api/orders
Body:
{
  "idempotentToken": "${__UUID()}",
  "items": [{"productId": 1, "skuId": 1, "quantity": 1, "price": 7999.00}],
  "totalAmount": 7999.00,
  "payAmount": 7999.00,
  "consignee": "压测用户",
  "phone": "13800138000",
  "address": "北京市朝阳区测试街道100号"
}
```

**用 JMeter 函数 `${__UUID()}` 保证每个请求幂等 Token 唯一。**

### 执行压测

```bash
# 非 GUI 模式执行
jmeter -n -t stress-test/ecommerce.jmx \
       -l stress-test/results/result.jtl \
       -e -o stress-test/report/

# 打开报告
# stress-test/report/index.html
```

### 结果分析要点

| 指标 | 优秀 | 合格 | 需优化 |
|:-----|:-----|:-----|:------|
| 平均响应时间 | < 200ms | < 500ms | > 500ms |
| 吞吐量 (TPS) | > 500 | > 200 | < 100 |
| 错误率 | 0% | < 1% | > 1% |
| P99 响应时间 | < 1s | < 3s | > 3s |

### P6：秒杀场景压测（新增）

**JMeter 配置**：
```
Thread Group: 500 线程, Ramp-up 1s, 持续 60s
HTTP Request: POST /api/seckill/{seckillId}?skuId=1
Header: Authorization: Bearer ${token}
```

**预期结果**：
- Sentinel Dashboard 显示 `pass=100/s, block=400/s`（限流生效）
- Redis 库存精确扣减，无超卖（库存为 0 后全部返回"已售罄"）
- 被限流请求返回 `{"code":50001,"message":"系统繁忙"}`
- 秒杀 TPS > 3000（纯 Redis 操作，无 DB/事务开销）

### Redis 内存淘汰策略验证（新增）

```bash
# 1. 向 Redis 写入超过 256MB 的数据
redis-cli --eval fill_redis.lua

# 2. 查看淘汰计数
redis-cli INFO stats | grep evicted_keys

# 3. 验证热点商品 Key 仍存在（LRU 生效）
redis-cli EXISTS product:detail:1
# 预期：1（未被淘汰）

# 4. 验证冷数据已被淘汰
redis-cli EXISTS product:detail:999
# 预期：0（已被 LRU 淘汰）
```

### 缓存一致性测试

```bash
# 1. 启动 200 并发查询某商品（走缓存）
# 2. 同时更新该商品（POST /api/products/1）
# 3. 验证：更新后的下一次查询读到的是最新数据，非旧缓存
# 4. 观察日志："MQ 兜底删除缓存" 确认延迟双删生效
```

---

## 6.3 Sentinel 限流集成与验证

### 添加依赖

```kotlin
// 在各服务 build.gradle.kts 中
implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-sentinel:${springCloudAlibabaVersion}")
```

### 添加限流注解

```java
// 对下单接口限流
@PostMapping
@SentinelResource(value = "createOrder", fallback = "createOrderFallback")
public Result<OrderVO> createOrder(@Valid @RequestBody CreateOrderRequest req) {
    Long userId = getCurrentUserId();
    return Result.success(orderService.createOrder(req, userId));
}

// 限流降级方法
public Result<OrderVO> createOrderFallback(CreateOrderRequest req, BlockException e) {
    return Result.error(429, "系统繁忙，请稍后重试（订单限流中）");
}
```

### 在 Sentinel Dashboard 配置限流规则

```
1. 打开 http://localhost:8858
2. 找到 order-service → 流控规则 → 新增
3. 资源名: createOrder
4. QPS: 100
5. 流控效果: 快速失败
```

### 压测验证限流

```bash
# 用 JMeter 以 200 QPS 压下单接口
# 预期：约 100 个请求被限流返回 429，100 个正常通过
# Sentinel Dashboard 实时监控通过 QPS 和拒绝 QPS
```

---

## 6.4 Docker 容器化部署

### Dockerfile

```dockerfile
# 每个服务模块下的 Dockerfile
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
```

### 应用 Docker Compose

```yaml
# docker/docker-compose.app.yml
version: "3.8"

services:
  gateway:
    build:
      context: ../gateway
      dockerfile: Dockerfile
    image: ecommerce/gateway:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    depends_on:
      - nacos

  user-service:
    build:
      context: ../user-service
      dockerfile: Dockerfile
    image: ecommerce/user-service:latest
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=docker

  product-service:
    build:
      context: ../product-service
      dockerfile: Dockerfile
    image: ecommerce/product-service:latest
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=docker

  order-service:
    build:
      context: ../order-service
      dockerfile: Dockerfile
    image: ecommerce/order-service:latest
    ports:
      - "8084:8084"
    environment:
      - SPRING_PROFILES_ACTIVE=docker

  cart-service:
    build:
      context: ../cart-service
      dockerfile: Dockerfile
    image: ecommerce/cart-service:latest
    ports:
      - "8083:8083"

  payment-service:
    build:
      context: ../payment-service
      dockerfile: Dockerfile
    image: ecommerce/payment-service:latest
    ports:
      - "8085:8085"

  inventory-service:
    build:
      context: ../inventory-service
      dockerfile: Dockerfile
    image: ecommerce/inventory-service:latest
    ports:
      - "8086:8086"
```

### 一键构建并启动

```bash
# 1. 构建 jar
./gradlew clean build -x test

# 2. 构建镜像
./gradlew :user-service:bootBuildImage  # 如果不用 Dockerfile
# 或用 docker build
for svc in gateway user-service product-service order-service cart-service payment-service inventory-service; do
  docker build -t ecommerce/$svc:latest $svc/
done

# 3. 启动中间件
docker compose -f docker/docker-compose.yml up -d

# 4. 等待 30 秒
sleep 30

# 5. 启动应用
docker compose -f docker/docker-compose.app.yml up -d

# 6. 查看状态
docker compose -f docker/docker-compose.app.yml ps
```

---

## 6.5 GitHub Actions CI 流水线

```yaml
# .github/workflows/ci.yml
name: CI Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: root123
          MYSQL_DATABASE: ecommerce
        ports:
          - 3306:3306
        options: >-
          --health-cmd="mysqladmin ping"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=3

      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379

      rabbitmq:
        image: rabbitmq:3-management-alpine
        ports:
          - 5672:5672
        env:
          RABBITMQ_DEFAULT_USER: admin
          RABBITMQ_DEFAULT_PASS: admin123

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Run Tests
        run: ./gradlew test

      - name: Upload Test Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/build/reports/tests/'

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Build JAR
        run: ./gradlew build -x test

      - name: Upload Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: jars
          path: '*/build/libs/*.jar'
```

---

## 6.6 部署验证清单

```bash
# 1. 所有容器运行正常
docker ps
# 预期看到：mysql, redis, rabbitmq, nacos, sentinel, gateway, user, product, order, cart, payment, inventory

# 2. 网关健康检查
curl http://localhost:8080/actuator/health

# 3. 通过网关访问各服务
curl http://localhost:8080/api/products/1

# 4. 前端可访问
# 打开 http://localhost:3000

# 5. API 文档可访问
# 打开 http://localhost:8080/doc.html
```

### WebSocket 连接测试（新增）

```bash
# 1. 使用 wscat 命令行工具连接
npm install -g wscat
wscat -c "ws://localhost:8080/ws?token=<JWT_TOKEN>"

# 2. 模拟秒杀场景 → 观察控制台输出
# 预期：看到 {"type":"ORDER_CREATED","title":"订单已生成","orderNo":"..."}
```

### P7：评价缓存压测（新增）

```bash
# JMeter 配置
Thread Group: 300 线程，Ramp-up 30s，持续 5 min
HTTP Request 1: GET /api/products/1/reviews?page=1&size=10
HTTP Request 2: GET /api/products/1/rating

# 预期结果
评价列表缓存命中：TPS > 2000, 响应 < 10ms
评分统计缓存命中：TPS > 5000, 响应 < 5ms
```

---

## 检查点

- [ ] 单元测试覆盖率 ≥ 80%（非 60%）
- [ ] **Testcontainers 集成测试全部通过**
- [ ] **Spring Cloud Contract 契约测试通过**
- [ ] **Cypress E2E 核心流程测试通过**
- [ ] 并发下单测试无超卖
- [ ] JMeter 压测 TPS 达到目标值
- [ ] 秒杀 500 并发无超卖，Sentinel 限流生效
- [ ] 缓存命中时 QPS 显著高于直查 DB
- [ ] Redis 淘汰策略 allkeys-lru 生效
- [ ] 缓存延迟双删测试通过
- [ ] Sentinel Dashboard 限流规则生效
- [ ] Docker 部署全部正常运行
- [ ] **GitHub Actions CI/CD 质量门禁全部通过**
- [ ] **ELK 集中日志 + Prometheus + Grafana 可正常访问**
- [ ] **数据库备份/恢复脚本执行成功**
- [ ] **蓝绿部署流程验证通过**
- [ ] **Trivy 镜像漏洞扫描通过**
- [ ] **OWASP 依赖安全扫描通过**
- [ ] 前端通过网关正常访问后端
- [ ] WebSocket 连接测试通过

---

## 6.7 生产级增强：可观测性全套搭建（核心新增）

### ELK 集中日志

在 `docker-compose.yml` 中添加：

```yaml
services:
  elasticsearch:
    image: elasticsearch:7.17.25
    container_name: ecommerce-es
    environment:
      - discovery.type=single-node
      - "ES_JAVA_OPTS=-Xms256m -Xmx512m"
      - xpack.security.enabled=false
    volumes: [es-data:/usr/share/elasticsearch/data]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9200"]
      interval: 15s; timeout: 10s; retries: 5
    profiles: [observability]
    restart: unless-stopped

  logstash:
    image: logstash:7.17.25
    volumes:
      - ./logstash/pipeline:/usr/share/logstash/pipeline:ro
      - app-logs:/logs:ro
    environment:
      LS_JAVA_OPTS: "-Xms128m -Xmx256m"
    profiles: [observability]
    restart: unless-stopped

  kibana:
    image: kibana:7.17.25
    ports: ["5601:5601"]
    environment:
      ELASTICSEARCH_HOSTS: http://elasticsearch:9200
    profiles: [observability]
    restart: unless-stopped
```

启动方式：`docker compose --profile observability up -d`

### Prometheus + Grafana 指标监控

```yaml
services:
  prometheus:
    image: prom/prometheus:v2.53
    ports: ["9090:9090"]
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--storage.tsdb.retention.time=30d'
    profiles: [observability]
    restart: unless-stopped

  grafana:
    image: grafana/grafana:11.1
    ports: ["3000:3000"]
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
    profiles: [observability]
    restart: unless-stopped
```

Grafana 预置 Dashboard：JVM 监控、HTTP QPS/P99、业务大盘。

### 核心告警规则

```yaml
# prometheus/alert-rules.yml
groups:
  - name: ecommerce
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.01
        for: 2m
        labels: { severity: critical }
        annotations:
          summary: "5xx 错误率超过 1%"

      - alert: ContainerRestarting
        expr: rate(container_last_seen{name=~"ecommerce-.*"}[10m]) > 0.3
        for: 2m
        labels: { severity: warning }
        annotations:
          summary: "容器频繁重启"
```

### SkyWalking 链路追踪（可选）

```yaml
services:
  skywalking-oap:
    image: apache/skywalking-oap-server:9.7.0
    environment:
      SW_STORAGE: elasticsearch
      SW_STORAGE_ES_CLUSTER_NODES: elasticsearch:9200
      JAVA_OPTS: "-Xms256m -Xmx512m"
    profiles: [observability]
    restart: unless-stopped

  skywalking-ui:
    image: apache/skywalking-ui:9.7.0
    ports: ["8088:8080"]
    environment:
      SW_OAP_ADDRESS: http://skywalking-oap:12800
    profiles: [observability]
    restart: unless-stopped
```

### 本地访问地址汇总

| 工具 | 地址 |
|:-----|:-----|
| Kibana（日志） | http://localhost:5601 |
| Grafana（监控） | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| SkyWalking UI | http://localhost:8088 |

---

## 6.8 生产级增强：CI/CD 完整流水线

更新 `.github/workflows/ci.yml` 为完整版：

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]
  push:
    tags: ['v*']           # 版本号 tag 触发 CD

jobs:
  # ===== 代码质量 =====
  quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - uses: gradle/actions/setup-gradle@v4

      - name: Checkstyle + SpotBugs
        run: ./gradlew checkstyleMain spotbugsMain -PCI=true

      - name: Unit Test + Coverage
        run: ./gradlew test jacocoTestReport

      - name: SonarQube Scan + Quality Gate
        run: ./gradlew sonarqube
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}

      - name: OWASP Dependency Check
        run: ./gradlew dependencyCheckAnalyze

  # ===== 集成测试 =====
  integration:
    runs-on: ubuntu-latest
    steps:
      - name: Integration Tests (Testcontainers)
        run: ./gradlew integrationTest

  # ===== E2E 测试 =====
  e2e:
    runs-on: ubuntu-latest
    steps:
      - name: Start services
        run: docker compose --profile core up -d
      - name: Cypress E2E
        uses: cypress-io/github-action@v6
        with: { working-directory: frontend }

  # ===== 构建镜像（tag 触发） =====
  build-and-push:
    if: startsWith(github.ref, 'refs/tags/v')
    needs: [quality, integration, e2e]
    runs-on: ubuntu-latest
    steps:
      - name: Build Docker Images
        run: docker compose -f docker-compose.build.yml build
      - name: Trivy Vulnerability Scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: 'ecommerce/gateway:latest'
          format: 'table'
          exit-code: '1'
          severity: 'CRITICAL,HIGH'
```

---

## 6.9 生产级增强：数据库备份与恢复

```bash
#!/bin/bash
# scripts/backup.sh

BACKUP_DIR=./data/backups
DATE=$(date +%Y%m%d_%H%M%S)

# MySQL 全量备份
docker exec ecommerce-mysql mysqldump \
  --all-databases --single-transaction --quick \
  | gzip > "$BACKUP_DIR/mysql-$DATE.sql.gz"

# Redis AOF 备份
docker exec ecommerce-redis redis-cli BGSAVE

# 保留 7 天
find "$BACKUP_DIR" -name "mysql-*" -mtime +7 -delete

echo "✅ 备份完成: mysql-$DATE.sql.gz"
```

```bash
#!/bin/bash
# scripts/restore.sh —— 恢复脚本

BACKUP_FILE=$1
gunzip < "$BACKUP_FILE" | docker exec -i ecommerce-mysql mysql -uroot -p${DB_ROOT_PASSWORD} ecommerce
echo "✅ 恢复完成: $BACKUP_FILE"
```

---

## 6.10 生产级增强：蓝绿部署方案（Docker 版）

```bash
#!/bin/bash
# scripts/blue-green-deploy.sh

cd /opt/ecommerce

# 1. 启动 Green 环境（新版本）
APP_VERSION=$1 docker compose -f docker-compose.green.yml up -d

# 2. 等待 Green 健康
for i in {1..12}; do
  ALL_HEALTHY=$(docker compose -f docker-compose.green.yml ps --format json | \
    jq -r 'select(.Health != "healthy") | .Service')
  if [ -z "$ALL_HEALTHY" ]; then
    echo "✅ Green 全部健康"
    break
  fi
  sleep 10
done

# 3. 切换 Nginx 流量到 Green
cp nginx/conf.d/ecommerce-green.conf nginx/conf.d/ecommerce.conf
docker exec ecommerce-nginx nginx -s reload

echo "✅ 流量已切换到版本 $1"
echo "回滚命令: cp nginx/conf.d/ecommerce-blue.conf nginx/conf.d/ecommerce.conf && docker exec ecommerce-nginx nginx -s reload"
```

---

## 6.11 检查点

- [ ] 所有单元测试通过（覆盖率 > 60% 核心模块）
- [ ] 并发下单测试无超卖（库存预占/实扣/释放正确）
- [ ] JMeter 压测 TPS 达到目标值
- [ ] **秒杀 500 并发无超卖，Sentinel 限流生效**（新增）
- [ ] 缓存命中时 QPS 显著高于直查 DB
- [ ] **Redis 淘汰策略 allkeys-lru 生效，热点 Key 未被淘汰**（新增）
- [ ] **缓存延迟双删测试：并发查询 + 更新后缓存一致**（新增）
- [ ] Sentinel Dashboard 限流规则生效
- [ ] Docker 部署全部正常运行
- [ ] GitHub Actions CI 流水线通过
- [ ] 前端通过网关正常访问后端
- [ ] **WebSocket 连接测试通过，消息推送正常**（新增）
- [ ] **评价缓存压测 TPS 达标**（新增）

---

## 下一步

部署成功 → **[阶段 7：答辩准备](./phase-7-答辩准备.md)**
