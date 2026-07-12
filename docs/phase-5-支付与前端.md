# 阶段 5：支付 + 搜索 + 管理后台实施方案

> **目标：** 模拟支付闭环、Elasticsearch 搜索（可选）、Vue 3 管理后台 + ECharts 数据看板。  
> **预计工时：** 20~25 小时（前端部分视熟练度浮动较大）  
> **前置：** 阶段 4 订单模块完成  

---

## Part A：支付服务（payment-service）

### A.1 目录结构

```
payment-service/
└── src/main/java/com/ecommerce/payment/
    ├── PaymentApplication.java
    ├── controller/
    │   └── PaymentController.java
    ├── service/
    │   ├── PaymentService.java
    │   └── impl/PaymentServiceImpl.java
    ├── mapper/
    │   └── PaymentMapper.java
    ├── entity/
    │   └── Payment.java
    └── dto/
        ├── PayRequest.java
        └── PayResponse.java
```

### A.2 模拟支付逻辑

```java
// service/impl/PaymentServiceImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;

    @Override
    @Transactional
    public PayResponse pay(PayRequest request) {
        // 1. 校验订单
        Orders order = getOrderByNo(request.getOrderNo());
        if (order.getStatus() != 0) {
            throw new BusinessException("订单状态异常，当前状态: " + order.getStatus());
        }

        // 2. 检查是否已有支付流水（防重复支付）
        Payment existing = paymentMapper.selectOne(
            new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderNo, request.getOrderNo())
                .eq(Payment::getStatus, 0));
        if (existing != null) {
            return PayResponse.builder()
                .payNo(existing.getPayNo())
                .message("已有进行中的支付")
                .build();
        }

        // 3. 生成支付流水号
        String payNo = "PAY" + IdUtil.getSnowflake().nextId();

        Payment payment = new Payment();
        payment.setPayNo(payNo);
        payment.setOrderNo(request.getOrderNo());
        payment.setUserId(order.getUserId());
        payment.setTotalAmount(order.getPayAmount());
        payment.setPayType(request.getPayType());
        payment.setStatus(0);  // 待支付
        paymentMapper.insert(payment);

        // 4. 模拟异步支付（1~3 秒后回调）
        simulateAsyncPay(payNo, request.getOrderNo());

        return PayResponse.builder()
            .payNo(payNo)
            .message("支付处理中")
            .build();
    }

    // ====== 模拟异步支付回调 ======
    private void simulateAsyncPay(String payNo, String orderNo) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(1000, 3000));
                // 模拟 90% 成功率
                if (ThreadLocalRandom.current().nextDouble() < 0.9) {
                    handlePaySuccess(payNo, orderNo);
                } else {
                    handlePayFail(payNo, orderNo, "支付通道繁忙");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Transactional
    public void handlePaySuccess(String payNo, String orderNo) {
        // 更新支付状态
        Payment payment = paymentMapper.selectByPayNo(payNo);
        payment.setStatus(1);
        payment.setCallbackTime(LocalDateTime.now());
        paymentMapper.updateById(payment);

        // 更新订单状态：待支付 → 已支付
        Orders order = getOrderByNo(orderNo);
        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // MQ 广播支付成功事件
        rabbitTemplate.convertAndSend("exchange.payment",
            "routing.payment.success", orderNo);

        log.info("支付成功: payNo={}, orderNo={}", payNo, orderNo);
    }
}
```

### A.3 Controller

```java
// controller/PaymentController.java
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public Result<PayResponse> pay(@Valid @RequestBody PayRequest request) {
        return Result.success(paymentService.pay(request));
    }

    @GetMapping("/status/{orderNo}")
    public Result<Integer> queryStatus(@PathVariable String orderNo) {
        Payment payment = paymentService.getPaymentByOrderNo(orderNo);
        return payment != null ? Result.success(payment.getStatus()) : Result.error("无支付记录");
    }
}
```

---

## Part B：Elasticsearch 搜索（可选，轻量版跳过）

### B.1 索引创建

```bash
# 创建商品索引
curl -X PUT "http://localhost:9200/products" -H "Content-Type: application/json" -d '{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "ik_smart_analyzer": { "type": "ik_smart" }
      }
    }
  },
  "mappings": {
    "properties": {
      "id":        { "type": "long" },
      "name":      { "type": "text", "analyzer": "ik_smart_analyzer" },
      "description": { "type": "text", "analyzer": "ik_smart_analyzer" },
      "categoryId": { "type": "long" },
      "price":     { "type": "double" },
      "sales":     { "type": "integer" },
      "image":     { "type": "keyword", "index": false },
      "status":    { "type": "integer" }
    }
  }
}'
```

### B.2 同步机制

```java
// service/EsSyncService.java
@Service
@Slf4j
@RequiredArgsConstructor
public class EsSyncService {

    private final ElasticsearchRestTemplate esTemplate;
    private final ProductMapper productMapper;

    // 全量同步
    @PostConstruct
    public void fullSync() {
        List<Product> products = productMapper.selectList(null);
        for (Product p : products) {
            try {
                esTemplate.save(p);  // Spring Data ES
            } catch (Exception e) {
                log.error("ES 同步失败: productId={}", p.getId(), e);
            }
        }
        log.info("ES 全量同步完成，共 {} 条", products.size());
    }

    // 增量同步（MQ 消费）
    @RabbitListener(queues = "queue.es.sync")
    public void handleProductChange(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product != null) {
            esTemplate.save(product);
        }
    }
}
```

### B.3 搜索接口

```java
// controller/SearchController.java
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final ElasticsearchRestTemplate esTemplate;

    @GetMapping
    public Result<List<ProductDoc>> search(@RequestParam String keyword,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.multiMatch(m -> m
                    .fields("name^3", "description")
                    .query(keyword)))
                .withPageable(PageRequest.of(page, size))
                .withHighlight(Highlight.builder()
                    .fields(Map.of("name", new HighlightFieldParameters.Builder().build()))
                    .build())
                .build();

        SearchHits<ProductDoc> hits = esTemplate.search(query, ProductDoc.class);
        return Result.success(hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList()));
    }
}
```

---

## Part C：Vue 3 管理后台

### C.1 项目初始化

```bash
cd D:\study\java全栈开发\Java全栈技术项目\frontend

# Vite 项目创建（选择 Vue + TypeScript）
npm create vite@latest . -- --template vue-ts

# 安装依赖
npm install
npm install element-plus axios vue-router@4 pinia echarts vue-echarts @element-plus/icons-vue
npm install -D @types/node sass
```

### C.2 目录结构

```
frontend/src/
├── api/                    # API 接口封装
│   ├── auth.ts             # 登录/注册
│   ├── product.ts          # 商品相关
│   ├── order.ts            # 订单相关
│   └── user.ts             # 用户相关
│
├── router/                 # 路由配置
│   └── index.ts
│
├── stores/                 # Pinia 状态管理
│   └── user.ts
│
├── views/                  # 页面组件
│   ├── login/
│   │   └── LoginView.vue
│   ├── dashboard/
│   │   └── DashboardView.vue
│   ├── product/
│   │   ├── ProductList.vue
│   │   └── ProductForm.vue
│   ├── order/
│   │   └── OrderList.vue
│   └── user/
│       └── UserList.vue
│
├── layout/
│   └── MainLayout.vue      # 主布局（侧边栏+顶栏+内容区）
│
├── components/             # 公共组件
├── utils/
│   └── request.ts          # axios 封装
├── App.vue
└── main.ts
```

### C.3 axios 封装（双 Token 自动刷新）

```typescript
// src/utils/request.ts
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：自动携带 Access Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：Token 过期自动刷新
let isRefreshing = false
let refreshSubscribers: ((token: string) => void)[] = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}
function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach(cb => cb(token))
  refreshSubscribers = []
}

request.interceptors.response.use(
  response => {
    const { code, message, data } = response.data
    if (code === 200) return data
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  async error => {
    const { config, response } = error
    if (response?.status === 401 && !config._retry) {
      config._retry = true
      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) {
        if (!isRefreshing) {
          isRefreshing = true
          try {
            const res = await axios.post('/api/auth/refresh', null, {
              params: { refreshToken }
            })
            const newToken = res.data.data.accessToken
            localStorage.setItem('accessToken', newToken)
            isRefreshing = false
            onTokenRefreshed(newToken)
            config.headers.Authorization = `Bearer ${newToken}`
            return request(config)
          } catch {
            isRefreshing = false
            localStorage.clear()
            router.push('/login')
          }
        } else {
          return new Promise(resolve => {
            subscribeTokenRefresh((token: string) => {
              config.headers.Authorization = `Bearer ${token}`
              resolve(request(config))
            })
          })
        }
      } else {
        router.push('/login')
      }
    }
    return Promise.reject(error)
  }
)

export default request
```

### C.4 路由配置

```typescript
// src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '数据看板' }
        },
        {
          path: 'products',
          name: 'Products',
          component: () => import('@/views/product/ProductList.vue'),
          meta: { title: '商品管理', role: 'ROLE_ADMIN' }
        },
        {
          path: 'orders',
          name: 'Orders',
          component: () => import('@/views/order/OrderList.vue'),
          meta: { title: '订单管理' }
        },
        {
          path: 'users',
          name: 'Users',
          component: () => import('@/views/user/UserList.vue'),
          meta: { title: '用户管理', role: 'ROLE_ADMIN' }
        }
      ]
    }
  ]
})

// 路由守卫：未登录跳登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('accessToken')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
```

### C.5 vite.config.ts（代理配置）

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // Gateway
        changeOrigin: true
      }
    }
  }
})
```

### C.6 登录页

```vue
<!-- src/views/login/LoginView.vue -->
<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2>电商后台管理系统</h2>
      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码"
            @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleLogin" block>
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const handleLogin = async () => {
  loading.value = true
  try {
    const res: any = await login(form.username, form.password)
    localStorage.setItem('accessToken', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
    localStorage.setItem('userInfo', JSON.stringify(res))
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch {
    ElMessage.error('登录失败')
  } finally {
    loading.value = false
  }
}
</script>
```

### C.7 商品管理页

```vue
<!-- src/views/product/ProductList.vue -->
<template>
  <div class="product-page">
    <el-card>
      <template #header>
        <span>商品管理</span>
        <el-button type="primary" @click="showDialog()" style="float: right">新增商品</el-button>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="商品名称" />
        <el-table-column prop="price" label="价格" width="120">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="100" />
        <el-table-column prop="sales" label="销量" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="showDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        :total="total"
        :page-size="size"
        @current-change="fetchData"
        layout="total, prev, pager, next"
        style="margin-top: 20px; justify-content: center"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProducts, deleteProduct } from '@/api/product'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await getProducts(page.value, size.value)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleDelete = async (id: number) => {
  await deleteProduct(id)
  ElMessage.success('删除成功')
  fetchData()
}

onMounted(fetchData)
</script>
```

### C.8 ECharts 数据看板

```vue
<!-- src/views/dashboard/DashboardView.vue -->
<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :span="6" v-for="card in statCards" :key="card.title">
        <el-card shadow="hover">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.title }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="16">
        <el-card><template #header>近 7 天订单趋势</template>
          <v-chart :option="orderChartOption" style="height: 350px" autoresize />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card><template #header>商品分类占比</template>
          <v-chart :option="categoryChartOption" style="height: 350px" autoresize />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'

use([CanvasRenderer, LineChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const statCards = [
  { title: '总商品数', value: 156 },
  { title: '今日订单', value: 42 },
  { title: '今日销售额', value: '¥12,580' },
  { title: '活跃用户', value: 89 }
]

const orderChartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: ['周一','周二','周三','周四','周五','周六','周日'] },
  yAxis: { type: 'value' },
  series: [{
    name: '订单数', type: 'line', smooth: true,
    data: [12, 18, 15, 22, 28, 35, 42],
    areaStyle: { opacity: 0.3 }
  }]
}))

const categoryChartOption = computed(() => ({
  tooltip: { trigger: 'item' },
  series: [{
    name: '分类', type: 'pie', radius: ['40%', '70%'],
    data: [
      { value: 45, name: '电子产品' },
      { value: 30, name: '服装' },
      { value: 15, name: '食品' },
      { value: 10, name: '其他' }
    ]
  }]
}))
</script>
```

---

## Part D：退款/退货逆向流程

### D.1 退款接口

```java
// controller/PaymentController.java 追加
@PostMapping("/{payNo}/refund")
public Result<Void> refund(@PathVariable String payNo, @RequestBody RefundRequest req) {
    paymentService.refund(payNo, req.getReason());
    return Result.success();
}
```

```java
// service/impl/PaymentServiceImpl.java 追加
@Override
@Transactional
public void refund(String payNo, String reason) {
    // 1. 查支付流水，确认已支付
    Payment payment = paymentMapper.selectOne(
        new LambdaQueryWrapper<Payment>().eq(Payment::getPayNo, payNo));
    if (payment == null || payment.getStatus() != 1) {
        throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
    }

    // 2. 查订单，确认状态允许退款（已支付/已发货）
    Orders order = orderMapper.selectOne(
        new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, payment.getOrderNo()));
    if (order.getStatus() != 1 && order.getStatus() != 2) {
        throw new BusinessException(400, "当前订单状态不支持退款");
    }

    // 3. 更新支付流水
    payment.setStatus(3);  // 已退款
    paymentMapper.updateById(payment);

    // 4. 更新订单状态
    order.setStatus(6);    // 已退款
    orderMapper.updateById(order);

    // 5. MQ 异步释放冻结库存（调用 inventory-service release）
    List<OrderItem> items = orderItemMapper.selectByOrderNo(order.getOrderNo());
    for (OrderItem item : items) {
        rabbitTemplate.convertAndSend("exchange.inventory",
            "routing.inventory.release",
            new StockChangeMessage(item.getSkuId(), item.getQuantity()));
    }

    log.info("退款处理完成: payNo={}, orderNo={}, reason={}", payNo, order.getOrderNo(), reason);
}
```

> **注意**：退款是真实电商的高风险操作，此处为模拟实现。真实场景需要：退款幂等校验、审计日志、资金原路返回。

---

## Part E：Nacos @RefreshScope 动态配置演示（答辩加分）

在 `product-service` 中配置 `@RefreshScope`，让缓存 TTL 可通过 Nacos 热刷新。

**Nacos 操作流程**：
1. 打开 `http://localhost:8848/nacos` → 配置列表 → `ecommerce-common.yml`
2. 修改 `cache.product-ttl: 300`（原 3600）→ 发布
3. 无需重启 → 缓存 TTL 变为 5 分钟
4. JMeter 压测中可观察到 Redis `expired_keys` 速率明显加快

> **答辩演示效果**：在压测过程中实时修改 TTL → Redis 监控面板 `keyspace_hits`/`expired_keys` 实时变化 → 证明配置热刷新生效。

---

## Part F：用户端模拟页面（3 页）

> 除管理后台外，增加极简用户端页面，答辩时演示完整操作链路。

### F.1 目录结构

```
frontend/src/
├── views/
│   ├── shop/                      # ⚡ 用户端页面
│   │   ├── ProductListView.vue    # 商品列表（搜索+分类）
│   │   ├── ProductDetailView.vue  # 商品详情（规格选择+加购）
│   │   └── CartCheckoutView.vue   # 购物车+确认下单
│   ├── seckill/                   # ⚡ 秒杀页面
│   │   └── SeckillView.vue        # 秒杀倒计时+抢购按钮
│   ├── login/
│   ├── dashboard/
│   └── ...
```

### F.2 商品列表页（核心代码）

```vue
<!-- views/shop/ProductListView.vue -->
<template>
  <div class="shop-page">
    <!-- 搜索栏 -->
    <el-input v-model="keyword" placeholder="搜索商品..." @keyup.enter="search"
              style="width: 400px; margin-bottom: 20px" />
    <!-- 分类筛选 -->
    <el-radio-group v-model="categoryId" @change="search">
      <el-radio-button :value="null">全部</el-radio-button>
      <el-radio-button v-for="cat in categories" :key="cat.id" :value="cat.id">
        {{ cat.name }}
      </el-radio-button>
    </el-radio-group>

    <!-- 商品网格 -->
    <el-row :gutter="16" style="margin-top: 20px">
      <el-col :span="6" v-for="p in products" :key="p.id">
        <el-card :body-style="{ padding: 0 }" shadow="hover"
                 @click="$router.push(`/shop/product/${p.id}`)">
          <img :src="p.image || 'https://picsum.photos/300/200'" style="width:100%;height:180px;object-fit:cover" />
          <div style="padding: 14px">
            <div style="font-size: 16px;font-weight:bold">{{ p.name }}</div>
            <div style="color: #f56c6c;font-size: 20px;margin-top: 8px">¥{{ p.price }}</div>
            <div style="color: #999;font-size: 12px;margin-top: 4px">已售 {{ p.sales }} 件</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 热榜 -->
    <el-card style="margin-top: 30px">
      <template #header>🔥 热门商品 Top-10</template>
      <div v-for="(id, idx) in hotIds" :key="id" style="cursor:pointer"
           @click="$router.push(`/shop/product/${id}`)">
        {{ idx + 1 }}. 商品 #{{ id }}
      </div>
    </el-card>
  </div>
</template>
```

### F.3 购物车 + 确认下单页

```vue
<!-- views/shop/CartCheckoutView.vue -->
<template>
  <div class="cart-page">
    <el-table :data="cartItems" @selection-change="onSelectChange">
      <el-table-column type="selection" />
      <el-table-column prop="productName" label="商品名称" />
      <el-table-column prop="quantity" label="数量" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.quantity" :min="1" :max="99" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="单价" width="120">
        <template #default="{ row }">¥{{ row.price }}</template>
      </el-table-column>
    </el-table>

    <el-divider />
    <div style="text-align:right;font-size:18px">
      实付金额：<span style="color:#f56c6c;font-weight:bold">¥{{ totalPay }}</span>
    </div>
    <el-button type="danger" size="large" @click="submitOrder" style="float:right;margin-top:20px">
      确认下单
    </el-button>
  </div>
</template>

<script setup lang="ts">
// 下单逻辑：
// 1. 先请求 GET /api/orders/idempotent-token
// 2. 获取 Token 后 POST /api/orders 提交订单
// 3. 下单成功后跳转支付页
</script>
```

---

## Part G：秒杀页面

```vue
<!-- views/seckill/SeckillView.vue -->
<template>
  <div class="seckill-page">
    <el-card v-if="!started">
      <h2>⏳ 秒杀即将开始</h2>
      <h1 style="color:#f56c6c;font-size:48px">{{ countdown }}</h1>
    </el-card>

    <el-card v-else>
      <el-image :src="product.image" style="width:300px;height:300px" />
      <h2>{{ product.name }}</h2>
      <h1 style="color:#f56c6c">秒杀价：¥{{ product.seckillPrice }}</h1>
      <el-button type="danger" size="large" :loading="ordering" @click="doSeckill" :disabled="soldOut">
        {{ soldOut ? '已售罄' : '立即抢购' }}
      </el-button>
      <div style="color:#999;margin-top:10px">
        剩余库存：{{ remainingStock }} / {{ totalStock }}
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'

const route = useRoute()
const seckillId = route.params.id

const countdown = ref('')
const started = ref(false)
const ordering = ref(false)
const soldOut = ref(false)

const doSeckill = async () => {
  ordering.value = true
  try {
    const res: any = await request.post(`/api/seckill/${seckillId}`, null, {
      params: { skuId: product.value.skuId }
    })
    ElMessage.success(res.message)  // "抢购成功，订单生成中..."
  } catch {
    ElMessage.error('抢购失败')
  } finally {
    ordering.value = false
  }
}

// ⚡ WebSocket 推送替代轮询：秒杀成功后等待订单推送
import { useWebSocket } from '@/composables/useWebSocket'
const { connect, onMessage } = useWebSocket()

onMounted(() => {
  // 连接 WebSocket（携带 JWT Token 认证）
  connect(`ws://localhost:8080/ws?token=${authStore.accessToken}`)

  // 监听订单生成推送
  onMessage((data) => {
    if (data.type === 'ORDER_CREATED') {
      ElNotification.success({ title: data.title, message: data.body })
      router.push(`/orders/${data.orderNo}`)  // 跳转支付
    }
  })
})

// 轮询倒计时 + 实时库存（WebSocket 更佳，简化用轮询）
setInterval(async () => {
  const res: any = await request.get(`/api/seckill/${seckillId}/status`)
  countdown.value = res.countdown
  started.value = res.started
  remainingStock.value = res.remainingStock
  soldOut.value = res.soldOut
}, 1000)
</script>
```

---

## Part H：WebSocket 前端 Composable（新增——基础设施）

```ts
// composables/useWebSocket.ts
import { ref, onUnmounted } from 'vue'

export function useWebSocket() {
  const ws = ref<WebSocket | null>(null)
  const handlers = ref<((data: any) => void)[]>([])

  function connect(url: string) {
    ws.value = new WebSocket(url)
    ws.value.onopen = () => console.log('[WS] 已连接')
    ws.value.onmessage = (event) => {
      const data = JSON.parse(event.data)
      handlers.value.forEach(fn => fn(data))
    }
    ws.value.onclose = () => console.log('[WS] 断开')
  }

  function onMessage(handler: (data: any) => void) {
    handlers.value.push(handler)
  }

  function disconnect() {
    ws.value?.close()
  }

  onUnmounted(() => disconnect())

  return { connect, onMessage, disconnect }
}
```

---

## Part I：我的订单页面（新增——业务闭环）

```vue
<!-- views/shop/MyOrdersView.vue -->
<template>
  <div class="my-orders">
    <el-tabs v-model="activeTab" @tab-click="fetchOrders">
      <el-tab-pane label="全部" name="" />
      <el-tab-pane label="待支付" name="0" />
      <el-tab-pane label="已支付" name="1" />
      <el-tab-pane label="已完成" name="4" />
    </el-tabs>

    <el-table :data="orders" v-loading="loading">
      <el-table-column prop="orderNo" label="订单号" width="180" />
      <el-table-column label="金额" width="120">
        <template #default="{row}">¥{{ row.payAmount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{row}">
          <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{row}">
          <el-button v-if="row.status===0" type="primary" size="small"
            @click="$router.push(`/pay/${row.orderNo}`)">去支付</el-button>
          <el-button v-if="row.status===3" type="success" size="small"
            @click="confirmReceive(row)">确认收货</el-button>
          <el-button v-if="row.status===4" type="warning" size="small"
            @click="openReview(row)">评价</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
const orders = ref([])
const activeTab = ref('')

const fetchOrders = async () => {
  const res = await request.get('/api/orders/my', {
    params: { page: 1, size: 20, status: activeTab.value || undefined }
  })
  orders.value = res.data.records
}

const confirmReceive = async (order) => {
  await request.put(`/api/orders/${order.orderNo}/receive`)
  ElMessage.success('已确认收货')
  fetchOrders()
}

const openReview = (order) => {
  // 跳转评价页，传 order.items 以便展示待评价商品
  router.push({ path: '/shop/review', query: { orderNo: order.orderNo } })
}

const statusText = (s: number) => ['待支付','已支付','已发货','已收货','已完成','已取消','已退款'][s]
</script>
```

---

## Part J：评价表单页面（新增）

```vue
<!-- views/shop/ReviewFormView.vue -->
<template>
  <div class="review-form">
    <h2>商品评价</h2>
    <div v-for="item in orderItems" :key="item.skuId" style="margin:20px 0">
      <el-card>
        <div style="display:flex;align-items:center;gap:12px">
          <span>{{ item.productName }}</span>
          <el-rate v-model="scores[item.skuId]" :max="5" />
        </div>
        <el-input v-model="contents[item.skuId]" type="textarea"
                  placeholder="分享你的使用体验（选填）" style="margin-top:10px" />
        <el-button type="primary" @click="submitReview(item)" style="margin-top:10px">提交评价</el-button>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()
const orderItems = ref([])
const scores = reactive({})
const contents = reactive({})

const submitReview = async (item) => {
  await request.post(`/api/products/${item.productId}/reviews`, {
    orderNo: route.query.orderNo,
    skuId: item.skuId,
    score: scores[item.skuId],
    content: contents[item.skuId]
  })
  ElMessage.success('评价成功')
}
</script>
```

---

## Part K：个人中心页面（新增——聚合入口）

```vue
<!-- views/shop/ProfileView.vue -->
<template>
  <el-tabs tab-position="left">
    <el-tab-pane label="我的订单">
      <MyOrdersView />
    </el-tab-pane>
    <el-tab-pane label="我的收藏">
      <el-row :gutter="16">
        <el-col :span="6" v-for="p in favorites" :key="p.id">
          <el-card @click="$router.push(`/shop/product/${p.id}`)">
            {{ p.name }} — ¥{{ p.price }}
          </el-card>
        </el-col>
      </el-row>
    </el-tab-pane>
    <el-tab-pane label="我的评价">
      <!-- 已评价列表 -->
    </el-tab-pane>
  </el-tabs>
</template>
```

---

## 检查点

- [ ] 下单后支持模拟支付，90% 成功率
- [ ] **支付宝沙箱扫码支付流程正常**（新增：真实沙箱环境）
- [ ] 支付成功 → 订单状态变为"已支付" + 库存实扣
- [ ] 支付失败 → 保持"待支付"状态
- [ ] 退款成功 → 支付流水/订单状态变更 + 库存释放
- [ ] ES 同步与搜索正常（可选）
- [ ] Vue 3 管理后台可正常登录
- [ ] 商品管理页 CRUD 正常
- [ ] 数据看板图表正常渲染
- [ ] Token 过期自动刷新机制有效
- [ ] 用户端商品列表/详情/购物车/下单页面正常
- [ ] 我的订单页面 + 状态Tab筛选 + 确认收货正常
- [ ] 评价表单页面 + 提交评价正常
- [ ] 个人中心（收藏列表）正常
- [ ] 秒杀倒计时/抢购按钮/库存实时展示正常
- [ ] WebSocket 连接正常，秒杀后自动弹窗通知
- [ ] Nacos 动态修改缓存 TTL 实时生效
- [ ] **前端 ESLint + Prettier 已配置，无 lint 错误**（新增）
- [ ] **Cypress E2E 测试：登录→浏览→加购→下单 全流程**（新增）

---

## 生产级增强：真实支付沙箱 + 前端工程化（新增）

### 真实支付沙箱接入

替代模拟支付，接入支付宝沙箱：

```bash
# 1. 注册支付宝开放平台（https://open.alipay.com）
# 2. 进入开发者中心 → 沙箱环境 → 获取 AppId + 密钥
# 3. 本地启动 ngrok 暴露公网回调地址
ngrok http 8080
# → Forwarding: https://abc123.ngrok-free.app → http://localhost:8080
# 4. 沙箱配置应用网关为 https://abc123.ngrok-free.app
```

```java
// 策略模式支持多支付渠道
public interface PaymentStrategy {
    String pay(PayOrderRequest request);
    boolean verifyNotify(Map<String, String> params);
}

@Service @Profile("prod")
class AlipayPayment implements PaymentStrategy { /* 支付宝 */ }

@Service @Profile("dev")
class MockPayment implements PaymentStrategy { /* 模拟支付，开发用 */ }
```

### 前端代码规范

```bash
# 前端项目安装 ESLint + Prettier
cd frontend
npm install -D eslint prettier eslint-plugin-vue @vue/eslint-config-typescript
```

```json
// frontend/.eslintrc.json
{
  "extends": ["plugin:vue/vue3-recommended", "@vue/eslint-config-typescript"],
  "rules": {
    "no-console": "warn",
    "vue/multi-word-component-names": "off"
  }
}
```

### Cypress E2E 测试（核心用户流程）

```javascript
// frontend/cypress/e2e/checkout.cy.js
describe('用户下单流程', () => {
  it('完整的浏览→加购→下单→支付流程', () => {
    cy.visit('/')
    cy.get('[data-cy=product-card]').first().click()
    cy.get('[data-cy=add-to-cart]').click()
    cy.get('[data-cy=cart-badge]').click()
    cy.get('[data-cy=checkout]').click()
    cy.get('[data-cy=place-order]').click()
    cy.contains('订单已生成').should('be.visible')
  })
})
```

---

## 下一步

所有核心功能完成 → **[阶段 6：集成测试 + 压测 + 部署](./phase-6-测试与部署.md)**
