# 阶段 1：项目脚手架与基础设施实施方案

> **目标：** 搭建 Gradle 多模块工程，Docker 一键启动全部中间件，完成 12 张核心表建表。  
> **预计工时：** 15~20 小时  
> **前置：** 阶段 0 环境准备全部完成  

---

## 1.1 在 IDEA 中创建 Gradle 多模块项目

整个项目结构通过 IDEA 的可视化向导创建，无需手动敲 `mkdir` 或复制 Gradle Wrapper 文件。

### 项目最终结构（预览）

```
D:\code\MyProject\Ecommerce-platform/             ← 项目根目录
├── docker/                          ← Docker Compose 编排文件
│   └── mysql/init.sql
├── docs/                            ← 项目文档
└── High-concurrency-ecommerce/      ← Gradle 多模块代码根目录
    ├── common/                      ← Java 库，无 main 方法
    ├── user-service/                ← Spring Boot 服务 (8081)
    ├── product-service/             ← Spring Boot 服务 (8082)
    ├── cart-service/                ← Spring Boot 服务 (8083)
    ├── order-service/               ← Spring Boot 服务 (8084)
    ├── payment-service/             ← Spring Boot 服务 (8085)
    ├── inventory-service/           ← Spring Boot 服务 (8086)
    ├── gateway/                     ← Spring Cloud Gateway (8080)
    ├── admin-service/               ← Spring Boot 服务 (8087)
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle.properties
    └── gradlew / gradlew.bat        ← IDEA 自动生成
```

---

### Step 1：创建根项目

1. 打开 IntelliJ IDEA，菜单栏点击 **File → New → Project…**
2. 左侧选择 **Spring Boot**（如果是 Community Edition 则选 **Gradle → Java**）
3. 填写项目信息：

| 字段 | 值 |
|:------|:----|
| **Name** | `High-concurrency-ecommerce` |
| **Location** | `D:\code\MyProject\Ecommerce-platform\High-concurrency-ecommerce` |
| **Language** | `Java` |
| **Type** | `Gradle - Kotlin`（推荐 Kotlin DSL） |
| **Group** | `com.ecommerce` |
| **JDK** | 选择本地已安装的 JDK 17 |
| **Java** | `17` |
| **Packaging** | `Jar` |

4. **Spring Boot 版本**选择 `3.5.16`（当前 3.x 最新稳定版）
5. 依赖暂时不勾选（后面会逐个模块添加），直接点击 **Create**

> **注意：** 如果 IDEA 下拉框中没有 `3.5.16`，可以先用 `3.5.x` 当前有的版本创建，然后在 `build.gradle.kts` 里手动改成 `3.5.16`，再 Refresh Gradle 即可。

> **如果是 Community Edition（无 Spring Boot 向导）**  
> 左侧选 **Gradle** → 勾选 **Java** 和 **Kotlin DSL build script**。  
> 创建完成后在 `build.gradle.kts` 中手动添加 Spring Boot 插件即可。

→ 创建完成后，IDEA 会自动生成 `gradlew`、`gradlew.bat`、`gradle-wrapper.jar` 和 `gradle-wrapper.properties`，无需手动处理。

---

### Step 2：清理根项目生成的 src 目录

根项目只是一个**容器**，不写业务代码，所以删除 IDEA 自动生成的 `src` 目录：

- 在 IDEA 左侧 Project 面板中右键 `src` → **Delete** → 勾选 "Safe delete"

---

### Step 3：创建 common 模块（纯 Java 库）

1. 右键根项目 `High-concurrency-ecommerce` → **New → Module…**
2. 左侧选 **Gradle → Java**（不要选 Spring Boot，common 是纯库）
3. **Name** 填 `common`
4. **Group** 自动继承 `com.ecommerce`
5. **JDK** 选 JDK 17
6. 点击 **Create**

→ IDEA 生成 `common/build.gradle.kts`。如果 `settings.gradle.kts` 里没自动追加 `include("common")`，手动加上即可。

---

### Step 4：创建 8 个 Spring Boot 服务模块（批量操作）

重复以下步骤 7 次，分别创建各业务服务 + gateway（共 8 个）：

1. 右键根项目 → **New → Module…**
2. 左侧选 **Spring Boot**（或 Gradle → Java）
3. **Name** 依次填：`user-service` / `product-service` / `cart-service` / `order-service` / `payment-service` / `inventory-service` / `gateway` / `admin-service`
4. 依赖选择：都勾选 **Spring Web**（后续手动调整）
5. 点击 **Create**

> **快速技巧：** 创建第一个 `user-service` 后，右键该模块 → **Copy Configuration** → 改名称 → 重复创建剩下的，不必每次都从头选依赖。

> ⚠️ 创建模块后，需要手动在 `settings.gradle.kts` 中追加对应的 `include("模块名")`。IDEA 版本不同，有时不会自动追加。

---

### Step 5：确认 settings.gradle.kts 内容

创建全部 9 个模块后，打开 `settings.gradle.kts`，确认内容如下：

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "High-concurrency-ecommerce"

include(
    "common",
    "user-service",
    "product-service",
    "cart-service",
    "order-service",
    "payment-service",
    "inventory-service",
    "gateway",
    "admin-service"
)
```

---

### Step 6：新建/修改 gradle.properties（统一版本管理）

IDEA 创建项目时不一定会自动生成此文件。如果根目录下没有 `gradle.properties`，右键根项目 → **New → File** → 输入 `gradle.properties` 新建。文件内容如下：

```properties
# JDK 编译等级
javaVersion=17

# Spring 生态
springBootVersion=3.5.16
springCloudVersion=2025.0.3
springCloudAlibabaVersion=2025.0.0.0

# 数据层
mybatisPlusVersion=3.5.7
redissonVersion=3.31.0

# 安全
jjwtVersion=0.12.5

# 工具
hutoolVersion=5.8.28
knife4jVersion=4.5.0
```

---

### Step 7：修改根 build.gradle.kts

将 IDEA 自动生成的根 `build.gradle.kts` **完全替换**为以下内容：

```kotlin
plugins {
    id("java")
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.ecommerce"
    version = "1.0.0"

    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/spring") }
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependencies {
        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
```

保存后，点击 IDEA 右上角弹出的 **Load Gradle Changes**（大象图标）同步依赖。

---

## 1.2 创建子模块 build.gradle.kts

### common 模块（纯 Java 库，不依赖 Spring Boot）

```kotlin
// common/build.gradle.kts
plugins {
    id("java-library")
}

dependencies {
    // Spring Web（提供 Validation、JSON 等）
    api("org.springframework.boot:spring-boot-starter-web")

    // Hutool 工具
    api("cn.hutool:hutool-all:${property("hutoolVersion")}")

    // Knife4j（API 文档）
    api("com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:${property("knife4jVersion")}")
}
```

### 业务模块通用模板

```kotlin
// user-service/build.gradle.kts（其他服务模块类似，换名字即可）
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Security（仅 user-service 需要）
    implementation("org.springframework.boot:spring-boot-starter-security")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")

    // MyBatis-Plus + MySQL
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:${property("mybatisPlusVersion")}")
    runtimeOnly("com.mysql:mysql-connector-j")

    // Redis + Redisson
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.redisson:redisson-spring-boot-starter:${property("redissonVersion")}")

    // Nacos
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery:${property("springCloudAlibabaVersion")}")
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config:${property("springCloudAlibabaVersion")}")

    // Sentinel（按需添加）
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-sentinel:${property("springCloudAlibabaVersion")}")
}
```

---

## 1.3 编写 common 模块基础代码

### 统一返回体

```java
// common/src/main/java/com/ecommerce/common/result/Result.java
package com.ecommerce.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
```

### 业务异常类

```java
// common/src/main/java/com/ecommerce/common/exception/BusinessException.java
package com.ecommerce.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(500, message);
    }
}
```

### 全局异常处理

```java
// common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java
package com.ecommerce.common.exception;

import com.ecommerce.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: [{}] {}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "服务器内部错误，请稍后重试");
    }
}
```

### 基础实体类（含自动填充）

```java
// common/src/main/java/com/ecommerce/common/entity/BaseEntity.java
package com.ecommerce.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class BaseEntity {
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
```

### MyBatis-Plus 自动填充处理器

```java
// common/src/main/java/com/ecommerce/common/config/MyMetaObjectHandler.java
package com.ecommerce.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

### 分页插件配置

```java
// common/src/main/java/com/ecommerce/common/config/MyBatisPlusConfig.java
package com.ecommerce.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

---

## 1.4 编写 Docker 中间件编排

### 创建 docker-compose.yml

```yaml
# docker/docker-compose.yml

services:
  # ==========================================
  # MySQL 8.0
  # ==========================================
  mysql:
    image: mysql:8.0
    container_name: ecommerce-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: ecommerce
    ports:
      - "3306:3306"
    volumes:
      - ./mysql/init.sql:/docker-entrypoint-initdb.d/init.sql
      - mysql-data:/var/lib/mysql
    command: --default-authentication-plugin=mysql_native_password
            --character-set-server=utf8mb4
            --collation-server=utf8mb4_unicode_ci

  # ==========================================
  # Redis 7.2
  # ==========================================
  redis:
    image: redis:7.2-alpine
    container_name: ecommerce-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  # ==========================================
  # RabbitMQ 3.13（含管理后台）
  # ==========================================
  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: ecommerce-rabbitmq
    ports:
      - "5672:5672"   # AMQP 协议
      - "15672:15672" # 管理后台
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: admin123

    depends_on:
      mysql:
        condition: service_started

  # ==========================================
  # Nacos 3.0（注册中心 + 配置中心）
  # 使用内嵌 Derby 数据库，无需外部 MySQL
  # ==========================================
  nacos:
    image: nacos/nacos-server:v3.0.3
    container_name: ecommerce-nacos
    ports:
      - "8848:8848"
      - "9848:9848"   # gRPC
    environment:
      MODE: standalone
      NACOS_AUTH_TOKEN: "bmFjb3MtZGV2LXNlY3JldC1rZXktZm9yLXRva2VuLWdlbmVyYXRpb24tMDAx"
      NACOS_AUTH_IDENTITY_KEY: "dev-identity"
      NACOS_AUTH_IDENTITY_VALUE: "dev-identity-value"

  # ==========================================
  # Sentinel Dashboard
  # ==========================================
  sentinel:
    image: bladex/sentinel-dashboard:1.8.9
    container_name: ecommerce-sentinel
    ports:
      - "8858:8858"
    environment:
      JAVA_OPTS: "-Dserver.port=8858 -Dcsp.sentinel.dashboard.server=localhost:8858"
    depends_on:
      nacos:
        condition: service_started

  # ==========================================
  # Elasticsearch 7.17（可选，轻量版跳过）
  # ==========================================
  elasticsearch:
    image: elasticsearch:7.17.20
    container_name: ecommerce-es
    environment:
      - discovery.type=single-node
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
      - xpack.security.enabled=false
    ports:
      - "9200:9200"
      - "9300:9300"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    profiles:
      - full

  # Kibana（可选）
  kibana:
    image: kibana:7.17.20
    container_name: ecommerce-kibana
    ports:
      - "5601:5601"
    environment:
      ELASTICSEARCH_HOSTS: http://elasticsearch:9200
    depends_on:
      elasticsearch:
        condition: service_started
    profiles:
      - full

volumes:
  mysql-data:
  redis-data:
  es-data:
```

### 启动中间件

> **注意：** 以下命令需在项目根目录 `D:\code\MyProject\Ecommerce-platform\` 下执行（即 `docker/` 所在目录），而非 `High-concurrency-ecommerce/` 子目录。

```bash
# 进入项目根目录
cd D:\code\MyProject\Ecommerce-platform

# 轻量版（推荐，不含 ES）
docker compose -f docker/docker-compose.yml up -d mysql redis rabbitmq nacos sentinel

# 完整版（含 ES）
docker compose -f docker/docker-compose.yml --profile full up -d

# 查看状态
docker compose -f docker/docker-compose.yml ps

# 查看日志
docker compose -f docker/docker-compose.yml logs -f nacos
```

---

## 1.5 数据库建表

### 创建初始化脚本

```sql
-- docker/mysql/init.sql

CREATE DATABASE IF NOT EXISTS ecommerce
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ecommerce;

-- ========================================
-- 1. 用户表
-- ========================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(255) NOT NULL COMMENT '加密密码',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `status`      TINYINT      DEFAULT 1 COMMENT '状态: 1正常 0禁用',
    `deleted`     TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ========================================
-- 2. 角色表
-- ========================================
CREATE TABLE IF NOT EXISTS `role` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(50) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255) DEFAULT NULL,
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ========================================
-- 3. 用户角色关联表
-- ========================================
CREATE TABLE IF NOT EXISTS `user_role` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ========================================
-- 4. 权限表
-- ========================================
CREATE TABLE IF NOT EXISTS `permission` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(100) NOT NULL COMMENT '权限标识',
    `description` VARCHAR(255) DEFAULT NULL,
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- ========================================
-- 5. 角色权限关联表
-- ========================================
CREATE TABLE IF NOT EXISTS `role_permission` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `role_id`       BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ========================================
-- 6. 商品分类表
-- ========================================
CREATE TABLE IF NOT EXISTS `category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(100) NOT NULL COMMENT '分类名称',
    `parent_id`   BIGINT       DEFAULT 0 COMMENT '父分类ID',
    `sort_order`  INT          DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ========================================
-- 7. 商品表
-- ========================================
CREATE TABLE IF NOT EXISTS `product` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(200)   NOT NULL COMMENT '商品名称',
    `description` TEXT           COMMENT '商品描述',
    `category_id` BIGINT         NOT NULL COMMENT '分类ID',
    `price`       DECIMAL(10,2)  NOT NULL COMMENT '单价',
    `stock`       INT            NOT NULL DEFAULT 0 COMMENT '库存',
    `image`       VARCHAR(500)   DEFAULT NULL COMMENT '主图URL',
    `sales`       INT            DEFAULT 0 COMMENT '销量',
    `status`      TINYINT        DEFAULT 1 COMMENT '状态: 1上架 0下架',
    `deleted`     TINYINT        DEFAULT 0,
    `create_time` DATETIME       DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ========================================
-- 8. 商品 SKU 表
-- ========================================
CREATE TABLE IF NOT EXISTS `product_sku` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT,
    `product_id`  BIGINT         NOT NULL COMMENT '商品ID',
    `attrs`       VARCHAR(200)   NOT NULL COMMENT '规格属性(JSON)',
    `price`       DECIMAL(10,2)  NOT NULL COMMENT 'SKU价格',
    `stock`       INT            NOT NULL DEFAULT 0 COMMENT 'SKU库存',
    `version`     INT            DEFAULT 0 COMMENT '乐观锁版本号',
    `create_time` DATETIME       DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

-- ========================================
-- 9. 订单表
-- ========================================
CREATE TABLE IF NOT EXISTS `orders` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT,
    `order_no`      VARCHAR(32)    NOT NULL COMMENT '订单号',
    `user_id`       BIGINT         NOT NULL COMMENT '用户ID',
    `total_amount`  DECIMAL(12,2)  NOT NULL COMMENT '总金额',
    `pay_amount`    DECIMAL(12,2)  NOT NULL COMMENT '实付金额',
    `status`        TINYINT        NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已发货 3已收货 4已完成 5已取消 6已退款',
    `pay_type`      TINYINT        DEFAULT NULL COMMENT '支付方式',
    `pay_time`      DATETIME       DEFAULT NULL COMMENT '支付时间',
    `consignee`     VARCHAR(50)    DEFAULT NULL COMMENT '收货人',
    `phone`         VARCHAR(20)    DEFAULT NULL COMMENT '收货电话',
    `address`       VARCHAR(255)   DEFAULT NULL COMMENT '收货地址',
    `remark`        VARCHAR(500)   DEFAULT NULL COMMENT '备注',
    `cancel_reason` VARCHAR(255)   DEFAULT NULL COMMENT '取消原因',
    `deleted`       TINYINT        DEFAULT 0,
    `create_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ========================================
-- 10. 订单明细表
-- ========================================
CREATE TABLE IF NOT EXISTS `order_item` (
    `id`           BIGINT         NOT NULL AUTO_INCREMENT,
    `order_no`     VARCHAR(32)    NOT NULL COMMENT '订单号',
    `product_id`   BIGINT         NOT NULL COMMENT '商品ID',
    `sku_id`       BIGINT         DEFAULT NULL,
    `product_name` VARCHAR(200)   NOT NULL COMMENT '商品名称快照',
    `product_image` VARCHAR(500)  DEFAULT NULL,
    `sku_attrs`    VARCHAR(200)   DEFAULT NULL,
    `price`        DECIMAL(10,2)  NOT NULL COMMENT '购买单价',
    `quantity`     INT            NOT NULL COMMENT '购买数量',
    `subtotal`     DECIMAL(12,2)  NOT NULL COMMENT '小计',
    `create_time`  DATETIME       DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ========================================
-- 11. 支付流水表
-- ========================================
CREATE TABLE IF NOT EXISTS `payment` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT,
    `pay_no`        VARCHAR(32)    NOT NULL COMMENT '支付流水号',
    `order_no`      VARCHAR(32)    NOT NULL COMMENT '关联订单号',
    `user_id`       BIGINT         NOT NULL COMMENT '用户ID',
    `total_amount`  DECIMAL(12,2)  NOT NULL COMMENT '支付金额',
    `pay_type`      TINYINT        DEFAULT 1 COMMENT '支付方式',
    `status`        TINYINT        DEFAULT 0 COMMENT '0待支付 1成功 2失败 3已退款',
    `callback_time` DATETIME       DEFAULT NULL COMMENT '回调时间',
    `create_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_no` (`pay_no`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水表';

-- ========================================
-- 13. 评价表（新增）
-- ========================================
CREATE TABLE IF NOT EXISTS `review` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `product_id`  BIGINT       NOT NULL COMMENT '商品ID',
    `sku_id`      BIGINT       DEFAULT NULL COMMENT 'SKU ID',
    `order_no`    VARCHAR(32)  NOT NULL COMMENT '关联订单号',
    `user_id`     BIGINT       NOT NULL COMMENT '评价用户ID',
    `score`       TINYINT      NOT NULL COMMENT '评分(1~5)',
    `content`     VARCHAR(1000) DEFAULT NULL COMMENT '评价内容',
    `images`      VARCHAR(1000) DEFAULT NULL COMMENT '晒图(JSON数组)',
    `status`      TINYINT      DEFAULT 0 COMMENT '0待审核 1通过 2拒绝',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product` (`product_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';

-- ========================================
-- 14. 到货通知表（新增，可选——用 Redis Set 订阅更轻量）
-- ========================================
CREATE TABLE IF NOT EXISTS `stock_notification` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `sku_id`      BIGINT       NOT NULL COMMENT 'SKU ID',
    `user_id`     BIGINT       NOT NULL COMMENT '订阅用户ID',
    `notified`    TINYINT      DEFAULT 0 COMMENT '0未通知 1已通知',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sku_user` (`sku_id`, `user_id`),
    KEY `idx_sku_notified` (`sku_id`, `notified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='到货通知订阅表';
CREATE TABLE IF NOT EXISTS `order_message` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `order_no`     VARCHAR(32)  NOT NULL,
    `message_body` TEXT         NOT NULL COMMENT '消息体(JSON)',
    `exchange`     VARCHAR(100) NOT NULL COMMENT '目标交换机',
    `routing_key`  VARCHAR(100) NOT NULL COMMENT '路由键',
    `status`       TINYINT      DEFAULT 0 COMMENT '0待发送 1已发送 2已确认 3已失败',
    `retry_count`  INT          DEFAULT 0 COMMENT '重试次数',
    `max_retry`    INT          DEFAULT 3,
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表';

-- ========================================
-- 初始化角色和权限数据
-- ========================================
INSERT INTO `role` (`name`, `description`) VALUES
('ROLE_ADMIN', '管理员'),
('ROLE_USER', '普通用户');

INSERT INTO `permission` (`name`, `description`) VALUES
('product:list', '查看商品列表'),
('product:create', '创建商品'),
('product:update', '修改商品'),
('product:delete', '删除商品'),
('order:list', '查看订单列表'),
('order:detail', '查看订单详情'),
('order:ship', '订单发货'),
('user:list', '查看用户列表'),
('user:manage', '管理用户');

-- 管理员拥有全部权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, id FROM permission;

-- 普通用户只有查看权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM permission WHERE name IN ('product:list', 'order:list', 'order:detail');

-- 初始化测试分类
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
('电子产品', 0, 1),
('服装', 0, 2),
('食品', 0, 3),
('手机', 1, 1),
('电脑', 1, 2);
```

### 初始化数据库

```bash
# 方式 1：通过 Docker 自动初始化（init.sql 在 ./mysql/ 中）
# 首次启动 MySQL 容器时会自动执行 init.sql

# 方式 2：手动执行（如果之前已有 MySQL 容器）
docker exec -i ecommerce-mysql mysql -uroot -proot123 ecommerce < docker/mysql/init.sql
```

---

## 1.6 配置 Nacos

### Nacos 3.x 说明

Nacos 3.x 相比 2.x 的变化：
- **强制鉴权**：必须配置 `NACOS_AUTH_TOKEN`（JWT 签名密钥）和 `NACOS_AUTH_IDENTITY_KEY/VALUE`
- **内嵌数据库**：本方案使用 Nacos 自带的内嵌 Derby 数据库（standalone 模式），无需额外配置 MySQL，配置数据存储在容器内

如果你需要将 Nacos 配置持久化到 MySQL，参考 Nacos 官方文档配置外部数据源。

### 访问 Nacos

打开浏览器：`http://localhost:8848/nacos`  
默认账号密码：`nacos / nacos`

### 创建命名空间

命名空间管理 → 新建命名空间：
- 命名空间名：`ecommerce-dev`
- 命名空间 ID：`ecommerce-dev`

### 添加公共配置

配置管理 → 配置列表 → 选择 `ecommerce-dev` → 新建配置：

**Data ID：** `ecommerce-common.yml`  
**Group：** `DEFAULT_GROUP`  
**格式：** YAML

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/ecommerce?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: root123
  data:
    redis:
      host: localhost
      port: 6379
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: admin123
    virtual-host: /

# 缓存配置（可通过 Nacos @RefreshScope 动态刷新）
cache:
  product-ttl: 3600                 # 商品缓存基础过期时间（秒）
  product-random-offset: 1800       # 随机偏移量（防雪崩）
  bloom-expected-size: 100000       # 布隆过滤器预期容量

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
      id-type: ASSIGN_ID
```

---

## 1.7 Logback 日志配置（TraceId 全链路追踪）

在 `common` 模块的 `resources` 目录创建 `logback-spring.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 日志格式，%X{traceId} 输出链路追踪 ID -->
    <property name="LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/ecommerce.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/ecommerce-%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

**Gateway 端 TraceId 过滤器**（在 `gateway` 模块中）：

```java
// gateway/src/main/java/com/ecommerce/gateway/filter/TraceIdFilter.java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TraceIdFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 从请求头获取 TraceId，没有则生成
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 写入 MDC（通过 ThreadLocal 在后续日志中自动携带）
        MDC.put("traceId", traceId);

        // 透传给下游服务
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-Trace-Id", traceId)
                .build();

        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signalType -> MDC.clear());
    }
}
```

> **效果**：一条 `TraceId` 串联 `Gateway → Controller → Service → Redis → MySQL → MQ 消费者` 的完整调用链，排查问题时直接搜索 TraceId 即可定位。

---

## 1.8 统一异常码枚举

在 `common` 模块创建业务异常码枚举，替换硬编码的数字：

```java
// common/src/main/java/com/ecommerce/common/constant/ErrorCode.java
package com.ecommerce.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 用户模块 10000~19999
    USER_NOT_FOUND(10001, "用户不存在"),
    PASSWORD_ERROR(10002, "用户名或密码错误"),
    USERNAME_DUPLICATE(10003, "用户名已被注册"),
    PHONE_DUPLICATE(10004, "手机号已被注册"),
    ACCOUNT_DISABLED(10005, "账号已被禁用"),
    TOKEN_EXPIRED(10006, "登录已过期，请重新登录"),
    TOKEN_BLACKLISTED(10007, "Token 已失效"),

    // 商品模块 20000~29999
    PRODUCT_NOT_FOUND(20001, "商品不存在"),
    STOCK_INSUFFICIENT(20002, "库存不足"),
    PRODUCT_OFF_SHELF(20003, "商品已下架"),

    // 订单模块 30000~39999
    ORDER_NOT_FOUND(30001, "订单不存在"),
    ORDER_DUPLICATE(30002, "请勿重复提交订单"),
    ORDER_STATUS_ERROR(30003, "订单状态异常"),
    ORDER_PRICE_MISMATCH(30004, "订单金额不匹配"),
    ORDER_LIMIT_EXCEEDED(30005, "操作太频繁，请稍后重试"),

    // 支付模块 40000~49999
    PAY_DUPLICATE(40001, "已有进行中的支付"),
    PAY_FAILED(40002, "支付失败"),
    PAY_TIMEOUT(40003, "支付超时"),

    // 系统通用 50000~59999
    SYSTEM_ERROR(50000, "服务器内部错误，请稍后重试"),
    RATE_LIMITED(50001, "系统繁忙，请稍后重试（限流中）"),
    SERVICE_UNAVAILABLE(50002, "服务暂不可用"),

    // 评价模块 60000~69999（新增）
    REVIEW_DUPLICATE(60001, "您已评价过该商品"),
    REVIEW_ORDER_NOT_COMPLETED(60002, "仅已完成订单可评价"),
    REVIEW_SCORE_INVALID(60003, "评分须在 1~5 之间"),

    // WebSocket / 通知（新增）
    NOTIFICATION_SEND_FAILED(70001, "消息推送失败");

    private final int code;
    private final String message;
}
```

同时修改 `BusinessException`，支持传入 `ErrorCode`：

```java
// common/src/main/java/com/ecommerce/common/exception/BusinessException.java 追加
public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.code = errorCode.getCode();
}
```

---

## 1.9 WebSocket 实时推送基础设施（新增）

WebSocket 用于秒杀订单通知、支付结果推送、库存恢复提醒等场景，替代轮询模式。依赖 Spring WebSocket + Redis Pub/Sub。

### 9.1 common 模块添加 WebSocket 依赖

```kotlin
// common/build.gradle.kts 追加
dependencies {
    // ... 已有依赖 ...

    // WebSocket
    api("org.springframework.boot:spring-boot-starter-websocket")
}
```

### 9.2 Redis Pub/Sub 配置类（common 模块）

```java
// common/src/main/java/com/ecommerce/common/config/RedisPubSubConfig.java
package com.ecommerce.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer container(RedisConnectionFactory factory,
                                                     MessageListenerAdapter listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        // 订阅通知频道（按用户 ID 隔离）
        container.addMessageListener(listener, new PatternTopic("notify:user:*"));
        return container;
    }

    @Bean
    public MessageListenerAdapter listener(NotificationHandler handler) {
        return new MessageListenerAdapter(handler, "handleMessage");
    }
}
```

### 9.3 通知消息处理器（common 模块）

```java
// common/src/main/java/com/ecommerce/common/config/NotificationHandler.java
package com.ecommerce.common.config;

import com.ecommerce.common.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHandler {

    private final WebSocketSessionManager sessionManager;

    public void handleMessage(String message, String channel) {
        // channel 格式：notify:user:{userId}
        // 解析 userId → 查找对应 WebSocket Session → 推送
        String userId = channel.substring(channel.lastIndexOf(":") + 1);
        sessionManager.sendToUser(userId, message);
        log.info("推送通知: userId={}, msg={}", userId, message);
    }
}
```

### 9.4 WebSocket Session 管理器（common 模块）

```java
// common/src/main/java/com/ecommerce/common/websocket/WebSocketSessionManager.java
package com.ecommerce.common.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionManager {

    // userId → WebSocketSession（一对一）
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        sessions.put(userId, session);
        log.info("WebSocket 连接: userId={}, sessionId={}", userId, session.getId());
    }

    public void remove(String userId) {
        sessions.remove(userId);
        log.info("WebSocket 断开: userId={}", userId);
    }

    /**
     * 向指定用户推送消息
     */
    public void sendToUser(String userId, String message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("WebSocket 推送失败: userId={}", userId, e);
            }
        }
    }

    /**
     * 广播给所有在线用户
     */
    public void broadcast(String message) {
        sessions.forEach((userId, session) -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                log.error("WebSocket 广播失败: userId={}", userId, e);
            }
        });
    }
}
```

### 9.5 WebSocket 握手拦截器（认证校验）

```java
// common/src/main/java/com/ecommerce/common/websocket/WebSocketAuthInterceptor.java
package com.ecommerce.common.websocket;

import com.ecommerce.common.constant.ErrorCode;
import com.ecommerce.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Value("${jwt.secret}")
    private String secret;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpReq = servletRequest.getServletRequest();
            String token = httpReq.getParameter("token");
            if (token == null || token.isEmpty()) {
                log.warn("WebSocket 握手失败: 缺少 Token");
                return false;
            }
            try {
                Claims claims = Jwts.parser()
                        .setSigningKey(secret.getBytes())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                String userId = claims.getSubject();
                attributes.put("userId", userId);
                return true;
            } catch (Exception e) {
                log.warn("WebSocket 握手失败: Token 无效");
                return false;
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {}
}
```

### 9.6 WebSocket 消息格式（JSON 协议）

```json
{
  "type": "ORDER_CREATED",
  "title": "订单已生成",
  "body": "恭喜！您的订单 202407120001 已生成",
  "orderNo": "202407120001",
  "timestamp": 1720768000000
}
```

消息类型枚举：
| type | 场景 | 触发时机 |
|:-----|:-----|:---------|
| `ORDER_CREATED` | 秒杀/下单成功 | MQ 消费者生成订单后 |
| `PAY_SUCCESS` | 支付完成 | 支付回调成功 |
| `PAY_TIMEOUT` | 支付超时 | 延迟队列取消订单 |
| `REFUND_DONE` | 退款完成 | 退款流程结束 |
| `STOCK_ARRIVAL` | 商品到货 | 管理员补库存后 |
| `ORDER_SHIPPED` | 订单发货 | 管理员操作发货 |

### 9.7 Gateway WebSocket 路由配置

```yaml
# gateway/src/main/resources/application.properties 追加
# WebSocket 支持（Gateway 需要转发 Upgrade 请求）
spring.cloud.gateway.routes[0].id=websocket-route
spring.cloud.gateway.routes[0].uri=lb://order-service
spring.cloud.gateway.routes[0].predicates[0]=Path=/ws/**
spring.cloud.gateway.routes[0].metadata.websocket=true
```

---

## 1.10 Redis 内存限额配置（Docker）

在 `docker-compose.yml` 中为 Redis 容器添加内存限制和淘汰策略：

```yaml
redis:
  image: redis:7.2-alpine
  container_name: ecommerce-redis
  ports:
    - "6379:6379"
  volumes:
    - redis-data:/data
  command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
  # allkeys-lru：内存满时淘汰最近最少使用的 Key
  # 热点商品（高频访问 Key）自动保留，冷数据被淘汰
```

> **答辩演示**：向 Redis 写入超过 256MB 数据 → `redis-cli INFO stats` 查看 `evicted_keys` 变化 → 验证热点商品 Key 未被淘汰（LRU 生效）。

---

## 1.11 生产级工程质量配置（核心新增）

> **说明**：以下配置是"生产级"与"课程级"的关键分界线。代码规范、静态检测、Docker 健康检查、Flyway 数据库迁移——这些工具从项目第一天就接入，让每次代码提交都经过自动化质量审查。

### 11.1 代码质量工具链配置

在根 `build.gradle.kts` 中添加以下插件：

```kotlin
// 根 build.gradle.kts —— 完整版（质量工具链）
plugins {
    id("java")
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("checkstyle")       // 代码风格检查
    id("jacoco")           // 测试覆盖率
    id("org.sonarqube") version "5.1.0.4882"  // 代码质量平台
    id("org.owasp.dependencycheck") version "10.0.4" apply false  // 依赖漏洞扫描
}

allprojects {
    group = "com.ecommerce"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ===== Checkstyle 配置 =====
    checkstyle {
        toolVersion = "10.17.0"
        // CI 环境严格模式（0 警告），本地开发宽松模式
        maxWarnings = if (System.getenv("CI") == "true") 0 else 999
    }

    // ===== JaCoCo 覆盖率配置 =====
    jacoco {
        toolVersion = "0.8.12"
    }

    tasks.jacocoTestReport {
        reports {
            xml.required = true     // SonarQube 用
            html.required = true    // 本地查看
        }
    }

    tasks.jacocoTestCoverageVerification {
        violationRules {
            rule {
                limit {
                    minimum = "0.80".toBigDecimal()  // 最低 80%
                }
            }
        }
    }

    tasks.check {
        dependsOn(tasks.jacocoTestCoverageVerification)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // 输出测试结果到标准输出
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

// ===== SonarQube 配置 =====
sonarqube {
    properties {
        property("sonar.projectKey", "ecommerce-platform")
        property("sonar.projectName", "高并发电商系统")
        property("sonar.host.url", "http://localhost:9000")
        property("sonar.qualitygate.wait", "true")
        property("sonar.coverage.jacoco.xmlReportPaths",
            "**/build/reports/jacoco/test/jacocoTestReport.xml")
    }
}
```

### 11.2 Checkstyle 规则文件

创建 `config/checkstyle/google_checks.xml`（使用 Google Java Style）：

> 下载标准规则文件：`https://raw.githubusercontent.com/checkstyle/checkstyle/checkstyle-10.17.0/src/main/resources/google_checks.xml`

核心规则会检查：
- 缩进（4 空格）、行宽（100 字符）
- 禁止 `System.out.println`（必须用 Slf4j）
- 禁止未使用的 import
- 命名规范（类名大驼峰、方法名小驼峰）
- 缺失 Javadoc（公开方法）

### 11.3 Docker 健康检查（所有服务）

更新 `docker-compose.yml`，为所有服务添加 `healthcheck` 和 `restart` 策略：

```yaml
services:
  mysql:
    # ... 已有配置 ...
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped       # 崩溃自动重启

  redis:
    # ... 已有配置 ...
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
    restart: unless-stopped

  rabbitmq:
    # ... 已有配置 ...
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
      interval: 15s
      timeout: 10s
      retries: 5
    restart: unless-stopped
```

### 11.4 结构化 JSON 日志（Logstash 编码器）

在 `common` 模块的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    // ... 已有依赖 ...
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
}
```

更新 `logback-spring.xml` 添加 JSON Appender：

```xml
<!-- logback-spring.xml 追加：JSON 格式输出（供 Logstash 采集） -->
<appender name="JSON" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/ecommerce-json.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/ecommerce-json-%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>7</maxHistory>
    </rollingPolicy>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"appName":"ecommerce"}</customFields>
    </encoder>
</appender>

<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
    <appender-ref ref="JSON"/>   <!-- 新增 JSON 输出 -->
</root>
```

输出效果（单行 JSON，机器可解析）：

```json
{"@timestamp":"2026-07-12T15:30:00.123+08:00","level":"INFO","logger":"OrderService","traceId":"a1b2c3","message":"用户下单成功","appName":"ecommerce","context":{"orderNo":"ORD202607120001"}}
```

### 11.5 GitHub Actions CI 基线流水线

创建 `.github/workflows/ci.yml`：

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Checkstyle
        run: ./gradlew checkstyleMain checkstyleTest

      - name: Unit Test + Coverage
        run: ./gradlew test jacocoTestReport

      - name: Build
        run: ./gradlew build -x test    # 测试已在上一步运行
```

> 每次 `git push` 或创建 PR，GitHub 云端自动运行以上检查。**CI 全绿是合并 PR 的前提条件。**

### 11.6 数据库 Flyway 迁移（替换 init.sql 一次性方案）

在 `common` 模块添加 Flyway 依赖：

```kotlin
// common/build.gradle.kts
dependencies {
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
}
```

创建迁移脚本目录：`common/src/main/resources/db/migration/`

```bash
# 将 init.sql 拆分为 Flyway 迁移脚本
V1__init_schema.sql       # 14 张核心表
V2__init_data.sql         # 角色/权限/分类初始数据
```

> Flyway 优势：版本化管理每次数据库变更，自动按序执行，支持回滚。比 init.sql 更符合生产级标准。

---

## 1.12 验证基础设施

### 验证清单

```bash
# 1. 所有容器正常运行
docker ps
# 预期：看到 mysql, redis, rabbitmq, nacos, sentinel 5 个容器

# 2. MySQL 连接正常
docker exec -it ecommerce-mysql mysql -uroot -proot123 -e "SELECT COUNT(*) FROM ecommerce.user"

# 3. Redis 连接正常
docker exec -it ecommerce-redis redis-cli PING
# 预期输出：PONG

# 4. Nacos 控制台可访问
# 打开 http://localhost:8848/nacos

# 5. RabbitMQ 管理后台可访问
# 打开 http://localhost:15672（admin/admin123）

# 6. Sentinel Dashboard 可访问
# 打开 http://localhost:8858（sentinel/sentinel）
```

---

## 1.13 检查点

- [ ] Gradle 多模块项目结构创建完毕
- [ ] `./gradlew build` 编译通过
- [ ] **Checkstyle + JaCoCo + SonarQube 插件已配置**
- [ ] **`.github/workflows/ci.yml` 已创建，CI 流水线可运行**
- [ ] Docker 中间件（MySQL/Redis/RabbitMQ/Nacos/Sentinel）全部运行中
- [ ] **所有 Docker 服务已添加 healthcheck + restart 策略**
- [ ] Redis `maxmemory-policy allkeys-lru` 已配置
- [ ] 14 张核心表已建好（支持 Flyway 迁移），初始化数据写入成功
- [ ] Nacos 控制台可访问，公共配置（含 cache 段）已添加
- [ ] `logback-spring.xml` 配置完毕，**含 JSON 格式输出**
- [ ] Gateway `TraceIdFilter` 已实现，请求头透传 TraceId
- [ ] `ErrorCode` 枚举已创建，`BusinessException` 已支持 ErrorCode 构造
- [ ] WebSocket 基础设施已搭建
- [ ] IDEA 正确识别所有子模块，无红色错误

---

## 1.14 下一步

基础设施就绪后 → **[阶段 2：用户模块 + 安全体系](./phase-2-用户模块.md)**
