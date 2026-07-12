# 阶段 3：商品模块 + 缓存体系实施方案

> **目标：** 实现商品 CRUD + Redis 缓存（穿透/击穿/雪崩完整方案）+ Lua 库存扣减 + 热门排行。  
> **预计工时：** 20~30 小时（含 ES 则 30）  
> **前置：** 阶段 2 用户模块完成  

---

## 3.1 模块目录结构

```
product-service/
├── build.gradle.kts
└── src/main/
    ├── java/com/ecommerce/product/
    │   ├── ProductApplication.java
    │   │
    │   ├── controller/
    │   │   └── ProductController.java        # 商品CRUD + 搜索
    │   │
    │   ├── service/
    │   │   ├── ProductService.java           # 商品业务
    │   │   ├── ProductCacheService.java       # 缓存服务（核心）
    │   │   └── HotProductService.java         # 热榜服务
    │   │
    │   ├── mapper/
    │   │   └── ProductMapper.java
    │   │
    │   ├── entity/
    │   │   ├── Product.java
    │   │   ├── ProductSku.java
    │   │   └── Category.java
    │   │
    │   ├── dto/
    │   │   ├── ProductVO.java
    │   │   ├── ProductCreateRequest.java
    │   │   └── ProductQuery.java
    │   │
    │   └── config/
    │       └── RedisConfig.java              # Redis 序列化配置
    │
    └── resources/
        ├── application.yml
        ├── bootstrap.yml
        └── lua/
            └── stock_decrement.lua            # Lua 脚本
```

---

## 3.2 实体类

```java
// entity/Product.java
package com.ecommerce.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String description;
    private Long categoryId;
    private BigDecimal price;
    private Integer stock;
    private String image;
    private Integer sales;
    private Integer status;
}

// entity/ProductSku.java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku")
public class ProductSku extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long productId;
    private String attrs;      // JSON: {"颜色":"黑","尺寸":"M"}
    private BigDecimal price;
    private Integer stock;
    @Version
    private Integer version;   // 乐观锁
}
```

---

## 3.3 Mapper 层

```java
// mapper/ProductMapper.java
package com.ecommerce.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Select("SELECT id FROM product WHERE deleted = 0")
    List<Long> selectAllIds();

    @Select("SELECT * FROM product WHERE category_id = #{categoryId} AND status = 1 AND deleted = 0")
    List<Product> selectByCategory(Long categoryId);
}
```

---

## 3.4 Redis 配置

```java
// config/RedisConfig.java
package com.ecommerce.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

---

## 3.5 核心缓存服务（重点）

```java
// service/ProductCacheService.java
package com.ecommerce.product.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ecommerce.product.dto.ProductVO;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;
    private final RedissonClient redissonClient;

    private static final String CACHE_KEY = "product:detail:";
    private static final String LOCK_KEY = "lock:product:";

    // ====== 布隆过滤器（防缓存穿透） ======
    private final BloomFilter<Long> bloomFilter = BloomFilter.create(
            Funnels.longFunnel(),
            100_000,  // 预期插入量
            0.01      // 误判率 1%
    );

    @PostConstruct
    public void initBloomFilter() {
        List<Long> allIds = productMapper.selectAllIds();
        allIds.forEach(bloomFilter::put);
        log.info("布隆过滤器初始化完成，加载 {} 个商品ID", allIds.size());
    }

    // 新增商品时同步更新布隆过滤器
    public void addToBloomFilter(Long productId) {
        bloomFilter.put(productId);
    }

    // ====== 核心查询（含三级防护） ======
    public ProductVO getProductDetail(Long productId) {
        String cacheKey = CACHE_KEY + productId;

        // 第一层：布隆过滤器（防穿透）
        if (!bloomFilter.mightContain(productId)) {
            log.warn("布隆拦截: productId={}", productId);
            return null;
        }

        // 第二层：查 Redis 缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cached)) {
            log.info("缓存命中: productId={}", productId);
            return JSONUtil.toBean(cached, ProductVO.class);
        }

        // 第三层：查数据库（互斥锁防击穿）
        return getFromDBWithLock(productId, cacheKey);
    }

    // ====== DB 查询 + 互斥锁 ======
    private ProductVO getFromDBWithLock(Long productId, String cacheKey) {
        String lockKey = LOCK_KEY + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试加锁，等待 3 秒，锁 10 秒自动释放
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                try {
                    // 双重检查
                    String cached = redisTemplate.opsForValue().get(cacheKey);
                    if (StrUtil.isNotBlank(cached)) {
                        return JSONUtil.toBean(cached, ProductVO.class);
                    }

                    // 查数据库
                    Product product = productMapper.selectById(productId);
                    if (product == null) {
                        // 缓存空值，短过期，防穿透
                        redisTemplate.opsForValue().set(cacheKey, "",
                                30 + ThreadLocalRandom.current().nextLong(10),
                                TimeUnit.SECONDS);
                        return null;
                    }

                    ProductVO vo = toVO(product);

                    // 写入缓存，随机过期时间（防雪崩）
                    long base = 3600;  // 1 小时
                    long random = base + ThreadLocalRandom.current().nextLong(1800);
                    redisTemplate.opsForValue().set(cacheKey,
                            JSONUtil.toJsonStr(vo), random, TimeUnit.SECONDS);

                    log.info("缓存回填: productId={}, expire={}s", productId, random);
                    return vo;

                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                // 没抢到锁，返回降级数据
                log.warn("获取锁超时，走降级: productId={}", productId);
                Product product = productMapper.selectById(productId);
                return product != null ? toVO(product) : null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ====== 更新商品后删除缓存 ======
    public void evictCache(Long productId) {
        redisTemplate.delete(CACHE_KEY + productId);
        log.info("缓存已清除: productId={}", productId);
    }

    private ProductVO toVO(Product p) {
        ProductVO vo = new ProductVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setCategoryId(p.getCategoryId());
        vo.setPrice(p.getPrice());
        vo.setStock(p.getStock());
        vo.setImage(p.getImage());
        vo.setSales(p.getSales());
        return vo;
    }
}
```

---

## 3.6 热门商品排行榜（Redis ZSet）

```java
// service/HotProductService.java
package com.ecommerce.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HotProductService {

    private final StringRedisTemplate redisTemplate;
    private static final String HOT_KEY = "hot:products";

    // 浏览商品时 +1
    public void recordView(Long productId) {
        redisTemplate.opsForZSet().incrementScore(HOT_KEY,
                productId.toString(), 1);
    }

    // Top-N 热榜
    public List<Long> getHotProducts(int topN) {
        Set<String> top = redisTemplate.opsForZSet()
                .reverseRange(HOT_KEY, 0, topN - 1);
        if (top == null || top.isEmpty()) return Collections.emptyList();
        return top.stream().map(Long::valueOf).toList();
    }
}
```

---

## 3.7 库存扣减 Lua 脚本

```lua
-- resources/lua/stock_decrement.lua
-- KEYS[1] = stock key (stock:sku:{skuId})
-- ARGV[1] = 扣减数量

local key = KEYS[1]
local amount = tonumber(ARGV[1])

local stock = redis.call('GET', key)
if not stock then
    return -1   -- key 不存在
end

stock = tonumber(stock)
if stock < amount then
    return 0    -- 库存不足
end

redis.call('DECRBY', key, amount)
return 1        -- 扣减成功
```

### 库存服务集成

```java
// service/InventoryService.java
package com.ecommerce.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String STOCK_KEY = "stock:sku:";
    private static String LUA_SCRIPT = null;

    static {
        try {
            ClassPathResource resource = new ClassPathResource("lua/stock_decrement.lua");
            LUA_SCRIPT = FileCopyUtils.copyToString(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("加载 Lua 脚本失败", e);
        }
    }

    // 初始化库存到 Redis（商品上架时调用）
    public void initStock(Long skuId, int stock) {
        redisTemplate.opsForValue().set(STOCK_KEY + skuId, stock);
    }

    // 扣减库存（原子）
    public boolean decrement(Long skuId, int amount) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(LUA_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script, List.of(STOCK_KEY + skuId), amount);

        if (result == null || result <= 0) {
            log.warn("库存扣减失败: skuId={}, amount={}, result={}", skuId, amount, result);
            return false;
        }
        log.info("库存扣减成功: skuId={}, amount={}", skuId, amount);
        return true;
    }

    // 回滚库存
    public void rollback(Long skuId, int amount) {
        redisTemplate.opsForValue().increment(STOCK_KEY + skuId, amount);
        log.info("库存回滚: skuId={}, amount={}", skuId, amount);
    }
}
```

---

## 3.8 Controller 层

```java
// controller/ProductController.java
package com.ecommerce.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.result.Result;
import com.ecommerce.product.dto.ProductCreateRequest;
import com.ecommerce.product.dto.ProductUpdateRequest;
import com.ecommerce.product.dto.ProductVO;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.service.HotProductService;
import com.ecommerce.product.service.ProductCacheService;
import com.ecommerce.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductCacheService cacheService;
    private final HotProductService hotProductService;

    // ====== 商品详情（走缓存） ======
    @GetMapping("/{id}")
    public Result<ProductVO> getProduct(@PathVariable Long id) {
        // 记录热度
        hotProductService.recordView(id);
        // 缓存查询
        ProductVO vo = cacheService.getProductDetail(id);
        return vo != null ? Result.success(vo) : Result.error(404, "商品不存在");
    }

    // ====== 分页查询（直接查 DB，列表页通常不加缓存） ======
    @GetMapping
    public Result<IPage<Product>> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(productService.page(new Page<>(page, size)));
    }

    // ====== 热门商品 ======
    @GetMapping("/hot")
    public Result<List<Long>> hotProducts() {
        return Result.success(hotProductService.getHotProducts(10));
    }

    // ====== 创建商品 ======
    @PostMapping
    @PreAuthorize("hasAuthority('product:create')")
    public Result<Product> create(@Valid @RequestBody ProductCreateRequest req) {
        Product product = productService.create(req);
        cacheService.addToBloomFilter(product.getId());
        return Result.success(product);
    }

    // ====== 更新商品 ======
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:update')")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody ProductUpdateRequest req) {
        productService.update(id, req);
        cacheService.evictCache(id);  // 删缓存
        return Result.success();
    }

    // ====== 删除商品 ======
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        cacheService.evictCache(id);
        return Result.success();
    }
}
```

---

## 3.9 Service 层

```java
// service/ProductService.java
package com.ecommerce.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.product.dto.ProductCreateRequest;
import com.ecommerce.product.dto.ProductUpdateRequest;
import com.ecommerce.product.entity.Product;

public interface ProductService extends IService<Product> {
    Product create(ProductCreateRequest req);
    void update(Long id, ProductUpdateRequest req);
    void delete(Long id);
}
```

---

## 3.10 验证测试

```bash
# 1. 添加测试商品
curl -X POST http://localhost:8082/api/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_token>" \
  -d '{
    "name": "iPhone 15 Pro",
    "description": "苹果最新旗舰手机",
    "categoryId": 4,
    "price": 7999.00,
    "stock": 100,
    "image": "https://example.com/iphone15.jpg"
  }'

# 2. 查询商品详情（第一次查 DB，第二次查缓存）
curl http://localhost:8082/api/products/1

# 3. 验证缓存命中
# 观察控制台日志：第一次有 "缓存回填"，第二次有 "缓存命中"

# 4. 验证布隆过滤器
curl http://localhost:8082/api/products/99999
# 返回 404，日志输出 "布隆拦截"

# 5. 验证热门商品
curl http://localhost:8082/api/products/hot

# 6. 验证权限
# 用普通用户 Token 创建商品 → 403
curl -X POST http://localhost:8082/api/products \
  -H "Authorization: Bearer <user_token>" \
  -H "Content-Type: application/json" \
  -d '{...}'
```

---

## 3.11 缓存验证脚本（JMeter）

用 JMeter 模拟 200 并发查询同一商品 ID：

```
线程组：200 线程，持续 60 秒
HTTP 请求：GET /api/products/{id}
预期结果：
  - 有缓存时 TPS > 2000，平均响应 < 50ms
  - 无缓存时 TPS < 300，平均响应 > 200ms
  - 缓存击穿期间互斥锁保护，DB 只被查询 1 次
```

---

## 3.12 库存预占/实扣分离模型（优化重点）

> **核心思路**：将原先"下单直接扣→取消回滚"改为"下单预占→支付实扣→超时释放"，语义更清晰，高并发下更鲁棒。

### Redis 库存三键模型

```
stock:sku:{skuId}:total     → 总库存（初始化后不变，支付成功时 DECR）
stock:sku:{skuId}:available → 可用库存（= total - frozen，预占时 DECR）
stock:sku:{skuId}:frozen    → 冻结库存（预占时 INCR，支付成功/超时取消时 DECR）
```

### 预占库存 Lua 脚本

```lua
-- resources/lua/stock_freeze.lua
-- KEYS[1]: available key  (stock:sku:{skuId}:available)
-- KEYS[2]: frozen key     (stock:sku:{skuId}:frozen)
-- ARGV[1]: 预占数量

local available = redis.call('GET', KEYS[1])
local frozen = redis.call('GET', KEYS[2])

if not available then
    return -1  -- 库存未初始化
end

available = tonumber(available)
frozen = tonumber(frozen or 0)
local amount = tonumber(ARGV[1])

if available < amount then
    return 0   -- 可用库存不足
end

redis.call('DECRBY', KEYS[1], amount)  -- 可用库存减少
redis.call('INCRBY', KEYS[2], amount)  -- 冻结库存增加
return 1     -- 预占成功
```

### 实扣库存（支付成功回调时）

```lua
-- resources/lua/stock_confirm.lua
-- KEYS[1]: total key    (stock:sku:{skuId}:total)
-- KEYS[2]: frozen key   (stock:sku:{skuId}:frozen)
-- ARGV[1]: 实扣数量

redis.call('DECRBY', KEYS[1], ARGV[1])   -- 总库存减少
redis.call('DECRBY', KEYS[2], ARGV[1])   -- 冻结库存释放
return 1
```

### 释放冻结（超时取消/退款）

```lua
-- resources/lua/stock_release.lua
-- KEYS[1]: available key
-- KEYS[2]: frozen key
-- ARGV[1]: 释放数量

redis.call('INCRBY', KEYS[1], ARGV[1])   -- 可用库存恢复
redis.call('DECRBY', KEYS[2], ARGV[1])   -- 冻结库存减少
return 1
```

### 库存初始化（商品上架时）

```java
public void initStock(Long skuId, int totalStock) {
    redisTemplate.opsForValue().set("stock:sku:" + skuId + ":total", totalStock);
    redisTemplate.opsForValue().set("stock:sku:" + skuId + ":available", totalStock);
    redisTemplate.opsForValue().set("stock:sku:" + skuId + ":frozen", 0);
}
```

---

## 3.13 缓存一致性 —— 延迟双删 + MQ 重试

> **方案升级**：原先"先更新 DB → 删缓存"（Cache Aside）是最基础方案，高并发下存在短暂窗口期。引入延迟双删 + MQ 兜底。

```java
// service/ProductCacheService.java 追加方法
private final RabbitTemplate rabbitTemplate;

/**
 * 更新商品后的缓存一致性策略 —— 延迟双删 + MQ 兜底
 */
public void updateProductWithCache(Long productId, ProductUpdateRequest req) {
    String cacheKey = CACHE_KEY + productId;

    // 1. 第一次删除缓存
    redisTemplate.delete(cacheKey);

    // 2. 更新数据库
    productService.update(productId, req);

    // 3. 延迟 500ms 后再次删除（异步）
    CompletableFuture.runAsync(() -> {
        try {
            Thread.sleep(500);  // 略大于一次读请求耗时
            redisTemplate.delete(cacheKey);

            // 4. 兜底：发送 MQ 延迟消息，消费者再次确认删除
            rabbitTemplate.convertAndSend(
                "exchange.cache.evict", "routing.cache.evict",
                cacheKey, msg -> {
                    msg.getMessageProperties().setDelay(2000);
                    return msg;
                });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    });

    // 5. 同步布隆过滤器
    addToBloomFilter(productId);
}
```

```java
// 缓存删除 MQ 消费者（兜底）
@RabbitListener(queues = "queue.cache.evict")
public void handleCacheEvict(String cacheKey) {
    redisTemplate.delete(cacheKey);
    log.info("MQ 兜底删除缓存: {}", cacheKey);
}
```

---

## 3.14 Nacos @RefreshScope 动态缓存 TTL 演示

让缓存过期时间可通过 Nacos 动态调整，无需重启应用。

```java
// service/ProductCacheService.java 追加
@RefreshScope  // ⚡ Nacos 配置变更后自动刷新
@Service
public class ProductCacheService {

    @Value("${cache.product-ttl:3600}")
    private long productCacheTTL;           // 动态缓存 TTL

    @Value("${cache.product-random-offset:1800}")
    private long productCacheRandomOffset;  // 动态随机偏移

    // ... 在写入缓存时使用
    private void writeCache(String cacheKey, ProductVO vo) {
        long expire = productCacheTTL + ThreadLocalRandom.current().nextLong(productCacheRandomOffset);
        redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(vo), expire, TimeUnit.SECONDS);
    }
}
```

> **Nacos 配置变更流程**：打开 Nacos 控制台 → 修改 `ecommerce-common.yml` 中 `cache.product-ttl: 300` → 发布 → 无需重启 → 商品缓存 TTL 自动变为 5 分钟。
>
> **答辩演示**：在 JMeter 压测过程中实时修改 Nacos TTL → 观察 Redis `INFO stats` 中的 `expired_keys` 变化速率明显加快，证明配置热刷新生效。

---

## 3.15 秒杀场景库存预热（为阶段 4 秒杀做准备）

```java
// service/SeckillCacheService.java（product-service 中新增）
@Service
@Slf4j
@RequiredArgsConstructor
public class SeckillCacheService {

    private final StringRedisTemplate redisTemplate;
    private final BloomFilter<Long> seckillBloomFilter;

    private static final String SECKILL_STOCK_PREFIX = "seckill:stock:";
    private static final String SECKILL_USER_PREFIX = "seckill:user:";

    /**
     * 秒杀活动开始时，将库存预热到 Redis
     */
    public void preheatStock(Long seckillId, Long skuId, int totalStock) {
        String stockKey = SECKILL_STOCK_PREFIX + seckillId + ":" + skuId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(totalStock));
        log.info("秒杀库存预热: seckillId={}, skuId={}, stock={}", seckillId, skuId, totalStock);
    }

    /**
     * 秒杀 Lua 原子扣减（同一商品每人限购 1 件）
     */
    public Long seckillDecrement(Long seckillId, Long skuId, Long userId) {
        String luaScript = """
            local stockKey = KEYS[1]       -- seckill:stock:{seckillId}:{skuId}
            local userKey = KEYS[2]        -- seckill:user:{seckillId}:{userId}

            -- 已购买检查（防重复）
            if redis.call('EXISTS', userKey) == 1 then
                return -1
            end

            -- 库存扣减
            local stock = redis.call('GET', stockKey)
            if not stock or tonumber(stock) <= 0 then
                return 0
            end

            redis.call('DECR', stockKey)
            redis.call('SET', userKey, '1', 'EX', 3600)  -- 标记已购买
            return 1
            """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
        return redisTemplate.execute(script,
                List.of(
                    SECKILL_STOCK_PREFIX + seckillId + ":" + skuId,
                    SECKILL_USER_PREFIX + seckillId + ":" + userId
                ));
    }
}
```

---

## 3.16 评价与收藏系统（新增——业务闭环 + 缓存演练）

### 3.16.1 评价核心服务

```java
// service/ReviewService.java（product-service 中新增）
@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    private static final String REVIEW_CACHE_PREFIX = "product:reviews:";
    private static final String RATING_CACHE_PREFIX = "product:rating:";

    /**
     * 提交评价（仅"已完成"订单可评）
     */
    @Transactional
    public void publishReview(Long userId, ReviewRequest req) {
        // 1. 幂等校验：同一订单同一 SKU 只能评价一次
        if (reviewMapper.existsByOrderNoAndSkuId(req.getOrderNo(), req.getSkuId())) {
            throw new BusinessException(ErrorCode.REVIEW_DUPLICATE);
        }

        // 2. 保存评价（待审核）
        Review review = buildReview(userId, req);
        reviewMapper.insert(review);

        // 3. 异步刷新评价统计缓存（MQ 解耦）
        rabbitTemplate.convertAndSend("exchange.review",
            "routing.review.stat.refresh", Map.of("productId", req.getProductId()));

        log.info("评价提交成功: userId={}, productId={}", userId, req.getProductId());
    }

    /**
     * 分页查询评价列表（缓存优先）
     */
    public IPage<ReviewVO> getReviews(Long productId, int page, int size) {
        String cacheKey = REVIEW_CACHE_PREFIX + productId + ":page:" + page;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSONUtil.toBean(cached, new TypeReference<Page<ReviewVO>>() {}, false);
        }

        IPage<ReviewVO> result = reviewMapper.selectPage(productId, page, size);
        // 缓存 30 分钟
        redisTemplate.opsForValue().set(cacheKey,
            JSONUtil.toJsonStr(result), 1800, TimeUnit.SECONDS);
        return result;
    }

    /**
     * 评分统计（缓存优先，MQ 异步刷新）
     */
    public ProductRating getRating(Long productId) {
        String cacheKey = RATING_CACHE_PREFIX + productId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSONUtil.toBean(cached, ProductRating.class);
        }
        // 缓存未命中 → 查 DB 并写入缓存
        ProductRating rating = reviewMapper.selectRating(productId);
        redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(rating), 3600, TimeUnit.SECONDS);
        return rating;
    }
}
```

```java
// dto/ProductRating.java
@Data
public class ProductRating {
    private double avgScore;       // 平均分
    private int totalCount;        // 总评价数
    private int score1Count;       // 1分数量
    private int score2Count;       // 2分数量
    private int score3Count;       // 3分数量
    private int score4Count;       // 4分数量
    private int score5Count;       // 5分数量
}
```

### 3.16.2 收藏功能（Redis Set 实现）

```java
// service/FavoriteService.java
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final StringRedisTemplate redisTemplate;
    private static final String FAVORITE_PREFIX = "favorite:user:";

    public void addFavorite(Long userId, Long productId) {
        redisTemplate.opsForSet().add(FAVORITE_PREFIX + userId, String.valueOf(productId));
    }

    public void removeFavorite(Long userId, Long productId) {
        redisTemplate.opsForSet().remove(FAVORITE_PREFIX + userId, String.valueOf(productId));
    }

    public boolean isFavorited(Long userId, Long productId) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForSet().isMember(FAVORITE_PREFIX + userId, String.valueOf(productId)));
    }

    public List<Long> getFavorites(Long userId) {
        Set<String> ids = redisTemplate.opsForSet().members(FAVORITE_PREFIX + userId);
        return ids.stream().map(Long::valueOf).collect(Collectors.toList());
    }
}
```

### 3.16.3 到货通知订阅（Redis Set + Pub/Sub）

```java
// service/StockNotificationService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class StockNotificationService {

    private final StringRedisTemplate redisTemplate;
    private static final String NOTIFY_PREFIX = "notify:sku:";

    /** 用户订阅到货通知 */
    public void subscribe(Long skuId, Long userId) {
        redisTemplate.opsForSet().add(NOTIFY_PREFIX + skuId, String.valueOf(userId));
        log.info("到货通知订阅: skuId={}, userId={}", skuId, userId);
    }

    /**
     * 补库存时调用 → 遍历订阅用户 → Redis Pub/Sub 推送
     */
    public void notifyArrival(Long skuId) {
        Set<String> userIds = redisTemplate.opsForSet().members(NOTIFY_PREFIX + skuId);
        if (userIds == null || userIds.isEmpty()) return;

        String message = JSONUtil.toJsonStr(Map.of(
            "type", "STOCK_ARRIVAL",
            "skuId", skuId,
            "title", "您关注的商品已到货"
        ));

        for (String userId : userIds) {
            // 通过 Redis Pub/Sub 推送到 WebSocket
            redisTemplate.convertAndSend("notify:user:" + userId, message);
        }
        // 通知后清空订阅列表
        redisTemplate.delete(NOTIFY_PREFIX + skuId);
        log.info("到货通知推送完成: skuId={}, count={}", skuId, userIds.size());
    }
}
```

---

### 3.17 Controller 追加（评价/收藏/通知）

```java
// ProductController.java 追加 API
@PostMapping("/{productId}/reviews")
public Result<Void> publishReview(@PathVariable Long productId,
                                   @Valid @RequestBody ReviewRequest req) {
    reviewService.publishReview(getCurrentUserId(), req);
    return Result.success();
}

@GetMapping("/{productId}/reviews")
public Result<IPage<ReviewVO>> getReviews(@PathVariable Long productId,
                                           @RequestParam int page,
                                           @RequestParam int size) {
    return Result.success(reviewService.getReviews(productId, page, size));
}

@GetMapping("/{productId}/rating")
public Result<ProductRating> getRating(@PathVariable Long productId) {
    return Result.success(reviewService.getRating(productId));
}

@PostMapping("/{productId}/favorite")
public Result<Void> addFavorite(@PathVariable Long productId) {
    favoriteService.addFavorite(getCurrentUserId(), productId);
    return Result.success();
}

@DeleteMapping("/{productId}/favorite")
public Result<Void> removeFavorite(@PathVariable Long productId) {
    favoriteService.removeFavorite(getCurrentUserId(), productId);
    return Result.success();
}

@PostMapping("/sku/{skuId}/notify")
public Result<Void> subscribeStockNotify(@PathVariable Long skuId) {
    stockNotificationService.subscribe(skuId, getCurrentUserId());
    return Result.success();
}
```

---

## 3.18 检查点

- [ ] 商品 CRUD 全部正常
- [ ] 商品详情走缓存，第二次查询秒回
- [ ] 布隆过滤器成功拦截不存在 ID 的请求
- [ ] 更新商品走延迟双删 + MQ 兜底
- [ ] Nacos @RefreshScope 修改缓存 TTL 实时生效
- [ ] Lua 脚本库存预占/实扣/释放三个原子操作正常
- [ ] 热门商品排行按访问量更新
- [ ] 权限控制生效（admin 可增删改，user 只读）
- [ ] 评价提交+列表查询+评分统计正常
- [ ] 收藏添加/取消/列表正常
- [ ] 到货通知订阅+库存恢复推送正常
- [ ] **集成测试：Testcontainers 验证布隆过滤器 + 缓存三级防护**（新增）

---

## 3.19 生产级增强：集成测试要求（新增）

从本阶段开始，缓存核心逻辑必须有 Testcontainers 集成测试：

```java
@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ProductCacheIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
        .withExposedPorts(6379);

    @Test
    void shouldPreventCachePenetrationWithBloomFilter() {
        // 验证不存在 ID 被布隆过滤器拦截，不穿透到 DB
    }

    @Test
    void shouldPreventCacheBreakdownWithMutexLock() {
        // 并发请求同一过期 Key，只有第一个回源 DB
    }

    @Test
    void shouldPreventCacheAvalancheWithRandomTTL() {
        // 批量 Key 过期时间分散
    }
}
```

---

## 3.20 下一步

商品模块完成 → **[阶段 4：购物车 + 订单核心链路](./phase-4-订单模块.md)**
