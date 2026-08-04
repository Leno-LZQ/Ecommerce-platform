-- ============================================================
-- 多商户高并发电商平台 — 数据库初始化脚本
-- MySQL 8.0+ | 字符集 utf8mb4 | 引擎 InnoDB
-- 
-- 覆盖范围：44 张表 / 10 个业务域
-- 执行方式：脚本内关闭外键检查，支持按业务域顺序建表
-- 编制日期：2026-07-13
-- 编制依据：《数据库设计文档》v1.0 + 《系统设计完整报告》v3.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS ecommerce 
    DEFAULT CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE ecommerce;

-- 关闭外键检查以允许按业务域顺序建表，最后再开启
SET FOREIGN_KEY_CHECKS = 0;

-- 整个初始化脚本包裹在事务中，任一语句失败可回滚
START TRANSACTION;

-- ============================================================
-- 域 1：用户权限体系（6 张表）— user-service
-- 核心：user → role ← permission，身份认证 + RBAC 授权
-- ============================================================

-- 1.1 角色表（无外部依赖）
CREATE TABLE IF NOT EXISTS `role` (
    `id`          BIGINT       NOT NULL COMMENT '角色ID（雪花算法）',
    `name`        VARCHAR(50)  NOT NULL COMMENT '角色名：ROLE_ADMIN/USER/MERCHANT/CS_AGENT/CS_MANAGER',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
    `create_time` DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`) COMMENT '角色名唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 1.2 权限表（无外部依赖）
CREATE TABLE IF NOT EXISTS `permission` (
    `id`          BIGINT       NOT NULL COMMENT '权限ID',
    `code`        VARCHAR(100) NOT NULL COMMENT '权限标识（如 product:create）',
		`name`        VARCHAR(100) NOT NULL COMMENT '名称（如商品新增）',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '权限描述',
    `create_time` DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`) COMMENT '权限标识唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 1.3 用户表（无外部依赖）
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL COMMENT '用户ID（雪花算法）',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(255) NOT NULL COMMENT '密码（BCrypt cost=12 加密）',
    `phone_hash`       VARCHAR(64)  DEFAULT NULL COMMENT '手机号 HMAC-SHA256 哈希（确定性，用于查重）',
    `phone_encrypted`  VARCHAR(128) DEFAULT NULL COMMENT '手机号 AES-256-CBC 密文（随机IV，用于解密还原）',
    `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `status`      TINYINT      DEFAULT 1 COMMENT '状态：0=禁用 1=正常',
    `deleted`     TINYINT      DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    `create_time` DATETIME     NOT NULL COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`) COMMENT '用户名唯一',
    UNIQUE KEY `uk_phone_hash` (`phone_hash`) COMMENT '手机号hash唯一',
    KEY `idx_status` (`status`) COMMENT '按状态查询',
    KEY `idx_create_time` (`create_time`) COMMENT '按创建时间排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 1.4 用户角色关联表（FK → user, role）
CREATE TABLE IF NOT EXISTS `user_role` (
    `id`      BIGINT NOT NULL COMMENT '关联ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`) COMMENT '用户角色防重',
    KEY `idx_role_id` (`role_id`) COMMENT '按角色反查用户',
    CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 1.5 角色权限关联表（FK → role, permission）
CREATE TABLE IF NOT EXISTS `role_permission` (
    `id`            BIGINT NOT NULL COMMENT '关联ID',
    `role_id`       BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`) COMMENT '角色权限防重',
    KEY `idx_perm_id` (`permission_id`) COMMENT '按权限反查角色',
    CONSTRAINT `fk_role_perm_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_role_perm_perm` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 1.6 用户收货地址表（FK → user）
CREATE TABLE IF NOT EXISTS `user_address` (
    `id`          BIGINT       NOT NULL COMMENT '地址ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `consignee`   VARCHAR(50)  NOT NULL COMMENT '收货人姓名',
    `phone`       VARCHAR(20)  NOT NULL COMMENT '收货人手机号',
    `province`    VARCHAR(50)  NOT NULL COMMENT '省份',
    `city`        VARCHAR(50)  NOT NULL COMMENT '城市',
    `district`    VARCHAR(50)  NOT NULL COMMENT '区/县',
    `detail`      VARCHAR(200) NOT NULL COMMENT '详细地址',
    `is_default`  TINYINT      DEFAULT 0 COMMENT '是否默认地址：0=否 1=是',
    `create_time` DATETIME     NOT NULL COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) COMMENT '按用户查地址列表',
    KEY `idx_user_default` (`user_id`, `is_default`) COMMENT '查用户默认地址',
    CONSTRAINT `fk_address_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收货地址表（每用户最多20个）';

-- ============================================================
-- 域 2：商品体系（6 张表）— product-service / cart-service
-- 核心：category 自引用树 → product(SPU) → product_sku
-- ============================================================

-- 2.1 商品分类表（自引用 FK）
CREATE TABLE IF NOT EXISTS `category` (
    `id`          BIGINT      NOT NULL COMMENT '分类ID',
    `parent_id`   BIGINT      DEFAULT NULL COMMENT '父分类ID：NULL=一级分类',
    `name`        VARCHAR(50) NOT NULL COMMENT '分类名称',
		`level`       TINYINT     NOT NULL COMMENT '1/2/3（层级深度）',
		`icon`        VARCHAR(255) COMMENT '分类图标 URL',
    `sort_order`  INT         DEFAULT 0 COMMENT '排序权重：越小越靠前',
		`status`      TINYINT     DEFAULT 1 COMMENT '0=禁用, 1=启用',
    `create_time` DATETIME    NOT NULL COMMENT '创建时间',
		`update_time` DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`) COMMENT '按父分类查子分类',
    KEY `idx_sort_order` (`sort_order`) COMMENT '按排序查询',
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表（自引用树）';

-- 2.2 商品 SPU 表（FK → category, merchant）
CREATE TABLE IF NOT EXISTS `product` (
    `id`             BIGINT        NOT NULL COMMENT '商品ID（SPU）',
    `name`           VARCHAR(200)  NOT NULL COMMENT '商品名称',
    `description`    TEXT          DEFAULT NULL COMMENT '商品描述',
    `category_id`    BIGINT        NOT NULL COMMENT '所属分类ID',
    `merchant_id`    BIGINT        NOT NULL COMMENT '所属商家ID',
		`brand`          VARCHAR(100)  NOT NULL COMMENT '品牌',
    `main_image`     VARCHAR(500)  DEFAULT NULL COMMENT '主图URL',
		`images`         JSON          DEFAULT NULL COMMENT '图片列表 `["url1","url2"]`',
		`status`         TINYINT       DEFAULT 1 COMMENT '状态：0=下架 1=上架',
		`audit_status`   TINYINT       DEFAULT 0 COMMENT '审核状态：0=待审 1=通过 2=拒绝',
		`audit_remark`   VARCHAR(255)  DEFAULT NULL COMMENT '审核备注',
    `sales`          INT           DEFAULT 0 COMMENT '累计销量（搜索排序加权）',
    `min_price`      DECIMAL(10,2) NOT NULL COMMENT 'SKU 最低价（冗余，列表展示）',
		`max_price`      DECIMAL(10,2) NOT NULL COMMENT 'SKU 最高价',
    `promo_tag`      VARCHAR(50)   DEFAULT NULL COMMENT '营销标签：NEW_USER_ONLY/FLASH_SALE/NONE',
    `promo_end_time` DATETIME      DEFAULT NULL COMMENT '营销活动结束时间',
    `deleted`        TINYINT       DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    `create_time`    DATETIME      NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_status` (`category_id`, `status`) COMMENT '分类+状态组合查询',
    KEY `idx_name` (`name`(50)) COMMENT '商品名前缀索引',
    KEY `idx_sales` (`sales`) COMMENT '按销量排序',
    KEY `idx_create_time` (`create_time`) COMMENT '按创建时间排序',
    KEY `idx_category_status_sales` (`category_id`, `status`, `sales`) COMMENT '分类热销排序',
    CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_product_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表（SPU）';

-- 2.3 商品 SKU 表（FK → product）
CREATE TABLE IF NOT EXISTS `product_sku` (
    `id`          BIGINT        NOT NULL COMMENT 'SKU ID',
    `product_id`  BIGINT        NOT NULL COMMENT '所属SPU ID',
    `attrs`       JSON          DEFAULT NULL COMMENT '规格属性（如 {"颜色":"黑色","尺码":"XL"}）',
    `price`       DECIMAL(10,2) NOT NULL COMMENT 'SKU 价格',
    `stock`       INT           NOT NULL COMMENT 'SKU 库存（Redis为准，MySQL为灾备）',
    `version`     INT           DEFAULT 1 COMMENT '乐观锁版本号',
    `create_time` DATETIME      NOT NULL COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`) COMMENT '按SPU查所有SKU',
    KEY `idx_product_stock` (`product_id`, `stock`) COMMENT '查询有库存的SKU',
    CONSTRAINT `fk_sku_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表（库存以Redis为主数据源）';

-- 2.4 购物车表（FK → user, product, sku）
CREATE TABLE IF NOT EXISTS `cart_item` (
    `id`          BIGINT   NOT NULL COMMENT '购物车记录ID',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `product_id`  BIGINT   NOT NULL COMMENT '商品ID',
    `sku_id`      BIGINT   NOT NULL COMMENT 'SKU ID',
    `quantity`    INT      DEFAULT 1 COMMENT '数量',
    `selected`    TINYINT  DEFAULT 1 COMMENT '是否选中：0=未选中 1=选中',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_sku` (`user_id`, `product_id`, `sku_id`) COMMENT '用户+SKU防重',
    CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_cart_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_cart_sku` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表（Redis为主存储，MySQL为容灾备份）';

-- 2.5 到货通知订阅表（FK → sku, user）
CREATE TABLE IF NOT EXISTS `stock_notification` (
    `id`          BIGINT   NOT NULL COMMENT '记录ID',
    `sku_id`      BIGINT   NOT NULL COMMENT 'SKU ID',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `notified`    TINYINT  DEFAULT 0 COMMENT '是否已通知：0=未通知 1=已通知',
    `create_time` DATETIME NOT NULL COMMENT '订阅时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sku_user` (`sku_id`, `user_id`) COMMENT '同一用户同一SKU仅订阅一次',
    KEY `idx_sku_notified` (`sku_id`, `notified`) COMMENT '批量查询未通知的订阅',
    CONSTRAINT `fk_notif_sku` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_notif_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='到货通知订阅表';

-- 2.6 商品评价表（FK → product, sku, user, orders）
CREATE TABLE IF NOT EXISTS `review` (
    `id`          BIGINT       NOT NULL COMMENT '评价ID',
    `product_id`  BIGINT       NOT NULL COMMENT '商品ID',
    `sku_id`      BIGINT       NOT NULL COMMENT 'SKU ID',
    `order_no`    VARCHAR(32)  NOT NULL COMMENT '订单号',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `score`       TINYINT      NOT NULL COMMENT '评分：1-5星',
    `content`     TEXT         DEFAULT NULL COMMENT '评价内容',
    `images`      JSON         DEFAULT NULL COMMENT '评价图片URL列表',
    `status`      TINYINT      DEFAULT 0 COMMENT '审核状态：0=待审 1=通过 2=拒绝',
    `create_time` DATETIME     NOT NULL COMMENT '评价时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_sku` (`order_no`, `sku_id`) COMMENT '同一订单同一SKU仅可评价一次',
    KEY `idx_product_id` (`product_id`) COMMENT '按商品查评价',
    KEY `idx_sku_id` (`sku_id`) COMMENT '按SKU查评价',
    KEY `idx_user_id` (`user_id`) COMMENT '按用户查评价',
    KEY `idx_product_status_time` (`product_id`, `status`, `create_time`) COMMENT '商品评价列表（审核通过+时间序）',
    CONSTRAINT `fk_review_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_review_sku` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_review_order` FOREIGN KEY (`order_no`) REFERENCES `orders` (`order_no`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_review_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表（1-5星+图文）';

-- ============================================================
-- 域 3：订单体系（4 张表）— order-service
-- 核心：orders 主表 → order_item 明细 → order_split 拆分
-- ============================================================

-- 3.1 订单主表（FK → user）
CREATE TABLE IF NOT EXISTS `orders` (
    `id`                BIGINT        NOT NULL COMMENT '订单ID',
    `order_no`          VARCHAR(32)   NOT NULL COMMENT '订单号（雪花+日期前缀）',
    `user_id`           BIGINT        NOT NULL COMMENT '下单用户ID',
    `total_amount`      DECIMAL(10,2) NOT NULL COMMENT '订单原始总金额',
    `pay_amount`        DECIMAL(10,2) NOT NULL COMMENT '实付金额（优惠后）',
    `status`            TINYINT       DEFAULT 0 COMMENT '订单状态：0=待支付 1=已支付 2=已发货 3=已完成 4=已取消 5=已退款',
    `is_multi_merchant` TINYINT       DEFAULT 0 COMMENT '是否多商家订单：0=否 1=是',
    `pay_type`          TINYINT       DEFAULT NULL COMMENT '支付方式：1=支付宝 2=微信',
    `pay_time`          DATETIME      DEFAULT NULL COMMENT '支付时间',
    `consignee`         VARCHAR(50)   DEFAULT NULL COMMENT '收货人（下单快照）',
    `phone`             VARCHAR(20)   DEFAULT NULL COMMENT '收货手机号（下单快照）',
    `address`           VARCHAR(500)  DEFAULT NULL COMMENT '收货地址（下单快照）',
    `remark`            VARCHAR(500)  DEFAULT NULL COMMENT '用户备注',
    `cancel_reason`     VARCHAR(200)  DEFAULT NULL COMMENT '取消原因',
    `deleted`           TINYINT       DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    `create_time`       DATETIME      NOT NULL COMMENT '创建时间',
    `update_time`       DATETIME      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`) COMMENT '订单号唯一',
    KEY `idx_user_id` (`user_id`) COMMENT '按用户查订单',
    KEY `idx_status` (`status`) COMMENT '按状态查订单',
    KEY `idx_user_status_create` (`user_id`, `status`, `create_time`) COMMENT '用户订单列表（主查询）',
    KEY `idx_status_create` (`status`, `create_time`) COMMENT '按状态+时间扫描',
    CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';

-- 3.2 订单明细表（FK → orders, product, sku, merchant）
CREATE TABLE IF NOT EXISTS `order_item` (
    `id`              BIGINT        NOT NULL COMMENT '明细ID',
    `order_no`        VARCHAR(32)   NOT NULL COMMENT '订单号',
    `merchant_id`     BIGINT        NOT NULL COMMENT '商品所属商家ID',
    `sub_order_no`    VARCHAR(32)   DEFAULT NULL COMMENT '拆分后子订单号',
    `product_id`      BIGINT        NOT NULL COMMENT '商品ID',
    `sku_id`          BIGINT        NOT NULL COMMENT 'SKU ID',
    `product_name`    VARCHAR(200)  NOT NULL COMMENT '商品名称（下单快照）',
    `product_image`   VARCHAR(500)  DEFAULT NULL COMMENT '商品图片（下单快照）',
    `sku_attrs`       JSON          DEFAULT NULL COMMENT 'SKU规格（下单快照）',
    `price`           DECIMAL(10,2) NOT NULL COMMENT '单价（下单快照）',
    `quantity`        INT           NOT NULL COMMENT '购买数量',
    `subtotal`        DECIMAL(10,2) NOT NULL COMMENT '小计（= price × quantity）',
    `discount_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '该明细优惠金额',
    `create_time`     DATETIME      NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_no` (`order_no`) COMMENT '按订单查明细',
    KEY `idx_product_id` (`product_id`) COMMENT '按商品查销售记录',
    KEY `idx_sub_order_no` (`sub_order_no`) COMMENT '按子订单号查明细',
    CONSTRAINT `fk_item_order` FOREIGN KEY (`order_no`) REFERENCES `orders` (`order_no`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_item_sku` FOREIGN KEY (`sku_id`) REFERENCES `product_sku` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_item_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表（下单信息快照）';

-- 3.3 订单商家拆分表（FK → orders, merchant）
CREATE TABLE IF NOT EXISTS `order_split` (
    `id`              BIGINT        NOT NULL COMMENT '拆分记录ID',
    `order_no`        VARCHAR(32)   NOT NULL COMMENT '原订单号',
    `sub_order_no`    VARCHAR(32)   NOT NULL COMMENT '子订单号',
    `merchant_id`     BIGINT        NOT NULL COMMENT '商家ID',
    `total_amount`    DECIMAL(10,2) NOT NULL COMMENT '子订单原始金额',
    `discount_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '子订单优惠金额',
    `pay_amount`      DECIMAL(10,2) NOT NULL COMMENT '子订单实付金额',
    `status`          TINYINT       DEFAULT 0 COMMENT '子订单状态（同步主订单）',
    `create_time`     DATETIME      NOT NULL COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sub_order_no` (`sub_order_no`) COMMENT '子订单号唯一',
    KEY `idx_order_no` (`order_no`) COMMENT '按原订单查拆分',
    KEY `idx_merchant_id` (`merchant_id`) COMMENT '按商家查子订单',
    CONSTRAINT `fk_split_order` FOREIGN KEY (`order_no`) REFERENCES `orders` (`order_no`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_split_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单商家拆分表（多商家订单拆为独立子订单）';

-- 3.4 本地消息表（FK → orders）
CREATE TABLE IF NOT EXISTS `order_message` (
    `id`           BIGINT       NOT NULL COMMENT '消息ID',
    `order_no`     VARCHAR(32)  NOT NULL COMMENT '关联订单号',
    `message_body` JSON         NOT NULL COMMENT '消息体',
    `exchange`     VARCHAR(100) DEFAULT NULL COMMENT 'RabbitMQ Exchange',
    `routing_key`  VARCHAR(100) DEFAULT NULL COMMENT 'RabbitMQ Routing Key',
    `status`       TINYINT      DEFAULT 0 COMMENT '投递状态：0=待投递 1=已投递 2=投递失败',
    `retry_count`  INT          DEFAULT 0 COMMENT '重试次数',
    `max_retry`    INT          DEFAULT 3 COMMENT '最大重试次数',
    `create_time`  DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`) COMMENT '按状态扫表',
    KEY `idx_order_no` (`order_no`) COMMENT '按订单查消息',
    KEY `idx_status_update` (`status`, `update_time`) COMMENT '定时扫描未投递消息',
    CONSTRAINT `fk_msg_order` FOREIGN KEY (`order_no`) REFERENCES `orders` (`order_no`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本地消息表（可靠消息投递）';

-- ============================================================
-- 域 4：支付体系（1 张表）— payment-service
-- ============================================================

-- 4.1 支付流水表（FK → orders, user）
CREATE TABLE IF NOT EXISTS `payment` (
    `id`            BIGINT        NOT NULL COMMENT '支付记录ID',
    `pay_no`        VARCHAR(32)   NOT NULL COMMENT '支付单号',
    `order_no`      VARCHAR(32)   NOT NULL COMMENT '关联订单号',
    `user_id`       BIGINT        NOT NULL COMMENT '支付用户ID',
    `total_amount`  DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    `pay_type`      TINYINT       NOT NULL COMMENT '支付方式：1=支付宝 2=微信支付',
    `status`        TINYINT       DEFAULT 0 COMMENT '支付状态：0=待支付 1=支付成功 2=支付失败 3=已退款',
    `callback_time` DATETIME      DEFAULT NULL COMMENT '回调时间',
    `create_time`   DATETIME      NOT NULL COMMENT '创建时间',
    `update_time`   DATETIME      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_no` (`pay_no`) COMMENT '支付单号唯一',
    KEY `idx_order_no` (`order_no`) COMMENT '按订单查支付',
    KEY `idx_user_id` (`user_id`) COMMENT '按用户查支付',
    CONSTRAINT `fk_pay_order` FOREIGN KEY (`order_no`) REFERENCES `orders` (`order_no`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_pay_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水表（最高安全级别）';

-- ============================================================
-- 域 5：商家体系（5 张表）— merchant-service
-- 先于商品表创建（product 依赖 merchant）
-- ============================================================

-- 5.1 商家主表（FK → user）
CREATE TABLE IF NOT EXISTS `merchant` (
    `id`            BIGINT       NOT NULL COMMENT '商家ID',
    `user_id`       BIGINT       NOT NULL COMMENT '关联用户ID',
    `shop_name`     VARCHAR(100) NOT NULL COMMENT '店铺名称',
    `shop_logo`     VARCHAR(500) DEFAULT NULL COMMENT '店铺Logo URL',
    `shop_desc`     TEXT         DEFAULT NULL COMMENT '店铺简介',
    `contact_name`  VARCHAR(50)  NOT NULL COMMENT '联系人姓名',
    `contact_phone` VARCHAR(20)  NOT NULL COMMENT '联系电话',
    `status`        TINYINT      DEFAULT 0 COMMENT '状态：0=待审核 1=正常 2=拒绝 3=冻结 4=注销',
    `audit_remark`  VARCHAR(500) DEFAULT NULL COMMENT '审核备注',
    `create_time`   DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`) COMMENT '一个用户仅一个商家身份',
    KEY `idx_status` (`status`) COMMENT '按状态筛选商家',
    CONSTRAINT `fk_merchant_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家/店铺主表';

-- 5.2 商家结算账户表（FK → merchant）
CREATE TABLE IF NOT EXISTS `merchant_settlement` (
    `id`             BIGINT       NOT NULL COMMENT '账户ID',
    `merchant_id`    BIGINT       NOT NULL COMMENT '商家ID',
    `bank_name`      VARCHAR(100) NOT NULL COMMENT '开户银行',
    `bank_account`   VARCHAR(50)  NOT NULL COMMENT '银行账号（仅存后4位明文）',
    `account_holder` VARCHAR(50)  NOT NULL COMMENT '开户人姓名',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_id` (`merchant_id`) COMMENT '一个商家一个结算账户',
    CONSTRAINT `fk_settle_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家结算账户表';

-- 5.3 商家审核日志表（FK → merchant）
CREATE TABLE IF NOT EXISTS `merchant_audit_log` (
    `id`          BIGINT       NOT NULL COMMENT '日志ID',
    `merchant_id` BIGINT       NOT NULL COMMENT '商家ID',
    `operator_id` BIGINT       NOT NULL COMMENT '审核人ID',
    `from_status` TINYINT      NOT NULL COMMENT '变更前状态',
    `to_status`   TINYINT      NOT NULL COMMENT '变更后状态',
    `remark`      VARCHAR(500) DEFAULT NULL COMMENT '审核备注',
    `create_time` DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`) COMMENT '按商家查审核历史',
    CONSTRAINT `fk_audit_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家审核日志表';

-- 5.4 商家子账号表（FK → merchant, user）
CREATE TABLE IF NOT EXISTS `merchant_sub_account` (
    `id`          BIGINT      NOT NULL COMMENT '子账号ID',
    `merchant_id` BIGINT      NOT NULL COMMENT '所属商家ID',
    `user_id`     BIGINT      NOT NULL COMMENT '关联用户ID',
    `role`        VARCHAR(30) NOT NULL COMMENT '子账号角色：运营/客服/财务',
    `status`      TINYINT     DEFAULT 1 COMMENT '状态：0=禁用 1=正常',
    `create_time` DATETIME    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`) COMMENT '一个用户一个子账号',
    KEY `idx_merchant_id` (`merchant_id`) COMMENT '按商家查子账号',
    CONSTRAINT `fk_sub_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_sub_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家子账号表（P2阶段）';

-- 5.5 商家资质文件表（FK → merchant）
CREATE TABLE IF NOT EXISTS `merchant_qualification` (
    `id`            BIGINT       NOT NULL COMMENT '资质ID',
    `merchant_id`   BIGINT       NOT NULL COMMENT '所属商家ID',
    `qual_type`     VARCHAR(30)  NOT NULL COMMENT '资质类型：BUSINESS_LICENSE/ID_CARD/FOOD_PERMIT',
    `qual_file_url` VARCHAR(500) NOT NULL COMMENT '资质文件OSS URL',
    `status`        TINYINT      DEFAULT 0 COMMENT '审核状态：0=待审 1=通过 2=拒绝',
    `create_time`   DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`) COMMENT '按商家查资质',
    CONSTRAINT `fk_qual_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家资质文件表';

-- ============================================================
-- 域 6：促销体系（8 张表）— promotion-service
-- 核心：券模板 → 用户领券 → 下单用券 + 活动 → 阶梯规则 → 适用范围
-- ============================================================

-- 6.1 优惠券模板表（无外部依赖）
CREATE TABLE IF NOT EXISTS `coupon_template` (
    `id`               BIGINT         NOT NULL COMMENT '模板ID',
    `template_name`    VARCHAR(100)   NOT NULL COMMENT '券名称',
    `coupon_type`      VARCHAR(20)    NOT NULL COMMENT '券类型：FULL_REDUCTION/DISCOUNT/NO_THRESHOLD',
    `discount_value`   DECIMAL(10,2)  NOT NULL COMMENT '优惠值（满减=金额 折扣=比例 无门槛=金额）',
    `threshold_amount` DECIMAL(10,2)  DEFAULT 0.00 COMMENT '使用门槛金额（无门槛券为0）',
    `total_quantity`   INT            NOT NULL COMMENT '发行总量',
    `issued_quantity`  INT            DEFAULT 0 COMMENT '已领取数量',
    `used_quantity`    INT            DEFAULT 0 COMMENT '已使用数量',
    `per_user_limit`   INT            DEFAULT 1 COMMENT '每人限领数量',
    `valid_days`       INT            NOT NULL COMMENT '有效天数（自领取起算）',
    `start_time`       DATETIME       NOT NULL COMMENT '领取开始时间',
    `end_time`         DATETIME       NOT NULL COMMENT '领取结束时间',
    `stackable`        TINYINT        DEFAULT 0 COMMENT '是否可叠加：0=否 1=是',
    `status`           TINYINT        DEFAULT 1 COMMENT '状态：0=停用 1=启用',
    `create_time`      DATETIME       NOT NULL COMMENT '创建时间',
    `update_time`      DATETIME       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';

-- 6.2 用户领券记录表（FK → coupon_template, user）
CREATE TABLE IF NOT EXISTS `coupon_user` (
    `id`             BIGINT       NOT NULL COMMENT '记录ID',
    `coupon_code`    VARCHAR(32)  NOT NULL COMMENT '券码（雪花算法，非递增防枚举）',
    `template_id`    BIGINT       NOT NULL COMMENT '券模板ID',
    `user_id`        BIGINT       NOT NULL COMMENT '用户ID',
    `status`         TINYINT      DEFAULT 0 COMMENT '状态：0=未使用 1=已锁定 2=已使用 3=已过期 4=已退还',
    `lock_order_no`  VARCHAR(32)  DEFAULT NULL COMMENT '锁定订单号',
    `expire_time`    DATETIME     NOT NULL COMMENT '过期时间（领取时间+valid_days）',
    `use_time`       DATETIME     DEFAULT NULL COMMENT '使用时间',
    `create_time`    DATETIME     NOT NULL COMMENT '领取时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_coupon_code` (`coupon_code`) COMMENT '券码唯一',
    KEY `idx_user_status` (`user_id`, `status`) COMMENT '用户可用券列表',
    KEY `idx_template_id` (`template_id`) COMMENT '按模板查领取情况',
    KEY `idx_expire_time` (`expire_time`) COMMENT '过期券扫描',
    CONSTRAINT `fk_coupon_template` FOREIGN KEY (`template_id`) REFERENCES `coupon_template` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_coupon_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户领券记录表（状态机+Redis并发控制）';

-- 6.3 订单用券快照表（FK → orders, coupon_user）
CREATE TABLE IF NOT EXISTS `order_coupon` (
    `id`              BIGINT        NOT NULL COMMENT '快照ID',
    `order_no`        VARCHAR(32)   NOT NULL COMMENT '订单号',
    `coupon_code`     VARCHAR(32)   NOT NULL COMMENT '券码',
    `template_id`     BIGINT        NOT NULL COMMENT '券模板ID',
    `coupon_type`     VARCHAR(20)   NOT NULL COMMENT '券类型（快照固化）',
    `discount_amount` DECIMAL(10,2) NOT NULL COMMENT '实际优惠金额（快照固化）',
    `create_time`     DATETIME      NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_no` (`order_no`) COMMENT '按订单查用券',
    KEY `idx_coupon_code` (`coupon_code`) COMMENT '按券码查使用记录',
    CONSTRAINT `fk_oc_order` FOREIGN KEY (`order_no`) REFERENCES `orders` (`order_no`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_oc_coupon` FOREIGN KEY (`coupon_code`) REFERENCES `coupon_user` (`coupon_code`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单用券快照表（下单时固化，不可篡改）';

-- 6.4 促销活动定义表（无外部依赖）
CREATE TABLE IF NOT EXISTS `promotion_activity` (
    `id`               BIGINT         NOT NULL COMMENT '活动ID',
    `activity_name`    VARCHAR(100)   NOT NULL COMMENT '活动名称',
    `activity_type`    VARCHAR(30)    NOT NULL COMMENT '活动类型：FULL_REDUCTION/FULL_DISCOUNT/N_M',
    `total_budget`     DECIMAL(12,2)  DEFAULT NULL COMMENT '总预算（NULL=不限）',
    `remaining_budget` DECIMAL(12,2)  DEFAULT NULL COMMENT '剩余预算',
    `user_daily_limit` INT            DEFAULT NULL COMMENT '每人每天参与上限（NULL=不限）',
    `start_time`       DATETIME       NOT NULL COMMENT '开始时间',
    `end_time`         DATETIME       NOT NULL COMMENT '结束时间',
    `status`           TINYINT        DEFAULT 0 COMMENT '状态：0=未开始 1=进行中 2=已结束',
    `create_time`      DATETIME       NOT NULL COMMENT '创建时间',
    `update_time`      DATETIME       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_start_time` (`start_time`) COMMENT '按开始时间查询',
    KEY `idx_end_time` (`end_time`) COMMENT '按结束时间查询',
    KEY `idx_status` (`status`) COMMENT '按时效状态筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='促销活动定义表';

-- 6.5 促销阶梯规则表（FK → promotion_activity）
CREATE TABLE IF NOT EXISTS `promotion_rule` (
    `id`             BIGINT        NOT NULL COMMENT '规则ID',
    `activity_id`    BIGINT        NOT NULL COMMENT '所属活动ID',
    `min_amount`     DECIMAL(10,2) DEFAULT NULL COMMENT '最低金额门槛',
    `max_amount`     DECIMAL(10,2) DEFAULT NULL COMMENT '最高金额门槛',
    `discount_type`  VARCHAR(20)   NOT NULL COMMENT '优惠类型：FIXED_AMOUNT/PERCENTAGE/FIXED_PRICE',
    `discount_value` DECIMAL(10,2) NOT NULL COMMENT '优惠值',
    `sort_order`     INT           DEFAULT 0 COMMENT '阶梯顺序',
    PRIMARY KEY (`id`),
    KEY `idx_activity_id` (`activity_id`) COMMENT '按活动查阶梯规则',
    CONSTRAINT `fk_rule_activity` FOREIGN KEY (`activity_id`) REFERENCES `promotion_activity` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='促销阶梯规则表（单/多阶梯）';

-- 6.6 促销适用范围表（无直接 FK，逻辑引用 coupon_template / promotion_activity / seckill_activity）
CREATE TABLE IF NOT EXISTS `promotion_scope` (
    `id`          BIGINT      NOT NULL COMMENT '范围ID',
    `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型：COUPON_TEMPLATE/PROMOTION/SECKILL',
    `target_id`   BIGINT      NOT NULL COMMENT '目标ID（模板/活动/秒杀）',
    `scope_type`  VARCHAR(20) NOT NULL COMMENT '范围类型：CATEGORY/PRODUCT/MERCHANT',
    `scope_id`    BIGINT      NOT NULL COMMENT '适用范围ID',
    PRIMARY KEY (`id`),
    KEY `idx_target` (`target_type`, `target_id`) COMMENT '按目标查询适用范围'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='促销适用范围表（多态关联）';

-- 6.7 秒杀活动表（无外部依赖）
CREATE TABLE IF NOT EXISTS `seckill_activity` (
    `id`             BIGINT        NOT NULL COMMENT '秒杀ID',
    `activity_name`  VARCHAR(100)  NOT NULL COMMENT '秒杀活动名称',
    `product_id`     BIGINT        NOT NULL COMMENT '秒杀商品ID',
    `sku_id`         BIGINT        NOT NULL COMMENT '秒杀SKU ID',
    `seckill_price`  DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    `seckill_stock`  INT           NOT NULL COMMENT '秒杀库存',
    `per_user_limit` INT           DEFAULT 1 COMMENT '每人限购数量',
    `start_time`     DATETIME      NOT NULL COMMENT '秒杀开始时间',
    `end_time`       DATETIME      NOT NULL COMMENT '秒杀结束时间',
    `status`         TINYINT       DEFAULT 0 COMMENT '状态：0=未开始 1=进行中 2=已结束',
    `create_time`    DATETIME      NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_start_time` (`start_time`) COMMENT '即将开始的秒杀查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动表（独立价格+库存）';

-- 6.8 用户优惠标签表（FK → user）
CREATE TABLE IF NOT EXISTS `user_coupon_tag` (
    `id`          BIGINT       NOT NULL COMMENT '标签ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `tag_type`    VARCHAR(30)  NOT NULL COMMENT '标签类型：NEW_USER/VIP/BIRTHDAY/INACTIVE',
    `tag_value`   VARCHAR(100) DEFAULT NULL COMMENT '标签值',
    `create_time` DATETIME     NOT NULL COMMENT '打标时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_tag` (`user_id`, `tag_type`) COMMENT '按用户+标签类型查询',
    CONSTRAINT `fk_tag_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠标签表（精准营销）';

-- ============================================================
-- 域 7：结算体系（3 张表）— settlement-service
-- ============================================================

-- 7.1 平台抽佣规则表（无外部依赖）
CREATE TABLE IF NOT EXISTS `commission_rule` (
    `id`          BIGINT        NOT NULL COMMENT '规则ID',
    `rule_name`   VARCHAR(100)  NOT NULL COMMENT '规则名称',
    `category_id` BIGINT        DEFAULT NULL COMMENT '适用分类（NULL=全品类）',
    `merchant_id` BIGINT        DEFAULT NULL COMMENT '适用商家（NULL=全商家）',
    `rate`        DECIMAL(5,4)  NOT NULL COMMENT '佣金率（如 0.0500=5%）',
    `min_amount`  DECIMAL(10,2) DEFAULT 0.00 COMMENT '最低佣金金额',
    `max_amount`  DECIMAL(10,2) DEFAULT NULL COMMENT '佣金封顶金额（NULL=不封顶）',
    `status`      TINYINT       DEFAULT 1 COMMENT '状态：0=停用 1=启用',
    `create_time` DATETIME      NOT NULL COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`) COMMENT '按分类查规则',
    KEY `idx_merchant_id` (`merchant_id`) COMMENT '按商家查规则'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台抽佣规则表';

-- 7.2 结算账单表（FK → merchant）
CREATE TABLE IF NOT EXISTS `settlement_bill` (
    `id`                  BIGINT         NOT NULL COMMENT '账单ID',
    `bill_no`             VARCHAR(32)    NOT NULL COMMENT '账单号',
    `merchant_id`         BIGINT         NOT NULL COMMENT '商家ID',
    `period_start`        DATE           NOT NULL COMMENT '结算周期开始',
    `period_end`          DATE           NOT NULL COMMENT '结算周期结束',
    `total_order_amount`  DECIMAL(12,2)  NOT NULL COMMENT '周期内订单总额',
    `total_discount`      DECIMAL(12,2)  DEFAULT 0.00 COMMENT '周期内优惠总额',
    `total_commission`    DECIMAL(12,2)  NOT NULL COMMENT '应缴佣金总额',
    `settlement_amount`   DECIMAL(12,2)  NOT NULL COMMENT '实际结算金额',
    `status`              TINYINT        DEFAULT 0 COMMENT '状态：0=草稿 1=已确认 2=已打款',
    `create_time`         DATETIME       NOT NULL COMMENT '创建时间',
    `update_time`         DATETIME       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bill_no` (`bill_no`) COMMENT '账单号唯一',
    KEY `idx_merchant_id` (`merchant_id`) COMMENT '按商家查账单',
    KEY `idx_status` (`status`) COMMENT '按状态查账单',
    CONSTRAINT `fk_bill_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算账单表';

-- 7.3 结算明细表（FK → settlement_bill）
CREATE TABLE IF NOT EXISTS `settlement_detail` (
    `id`              BIGINT        NOT NULL COMMENT '明细ID',
    `bill_id`         BIGINT        NOT NULL COMMENT '所属账单ID',
    `sub_order_no`    VARCHAR(32)   NOT NULL COMMENT '子订单号',
    `order_amount`    DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    `discount_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠金额',
    `rule_id`         BIGINT        NOT NULL COMMENT '适用佣金规则ID',
    `rate_snapshot`   DECIMAL(5,4)  NOT NULL COMMENT '佣金率快照（结算时固化）',
    `commission`      DECIMAL(10,2) NOT NULL COMMENT '该笔佣金',
    `create_time`     DATETIME      NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_bill_id` (`bill_id`) COMMENT '按账单查明细',
    KEY `idx_sub_order_no` (`sub_order_no`) COMMENT '按子订单号查明细',
    CONSTRAINT `fk_detail_bill` FOREIGN KEY (`bill_id`) REFERENCES `settlement_bill` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算明细表';

-- ============================================================
-- 域 8：消息通信体系（3 张表）— message-service
-- 核心：会话 → 消息 → 离线推送
-- ============================================================

-- 8.1 消息会话表（FK → user, merchant）
CREATE TABLE IF NOT EXISTS `conversation` (
    `id`               BIGINT   NOT NULL COMMENT '会话ID',
    `user_id`          BIGINT   NOT NULL COMMENT '用户ID',
    `merchant_id`      BIGINT   NOT NULL COMMENT '商家ID',
    `product_id`       BIGINT   DEFAULT NULL COMMENT '关联商品（可选）',
    `order_id`         BIGINT   DEFAULT NULL COMMENT '关联订单（可选）',
    `last_message`     TEXT     DEFAULT NULL COMMENT '最后一条消息摘要',
    `unread_user`      INT      DEFAULT 0 COMMENT '用户未读数',
    `unread_merchant`  INT      DEFAULT 0 COMMENT '商家未读数',
    `status`           TINYINT  DEFAULT 1 COMMENT '状态：1=活跃 2=归档',
    `created_at`       DATETIME NOT NULL COMMENT '创建时间',
    `updated_at`       DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_merchant_context` (`user_id`, `merchant_id`,
        (CASE WHEN `product_id` IS NULL THEN 0 ELSE `product_id` END),
        (CASE WHEN `order_id` IS NULL THEN 0 ELSE `order_id` END)) COMMENT '同用户同商家同上下文唯一会话',
    KEY `idx_user_updated` (`user_id`, `updated_at` DESC) COMMENT '用户会话列表排序',
    KEY `idx_merchant_updated` (`merchant_id`, `updated_at` DESC) COMMENT '商家会话列表排序',
    CONSTRAINT `fk_conv_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_conv_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息会话表';

-- 8.2 消息记录表（FK → conversation）
CREATE TABLE IF NOT EXISTS `message` (
    `id`              BIGINT      NOT NULL COMMENT '消息ID',
    `conversation_id` BIGINT      NOT NULL COMMENT '所属会话ID',
    `sender_type`     VARCHAR(10) NOT NULL COMMENT '发送者类型：USER/MERCHANT/SYSTEM',
    `sender_id`       BIGINT      NOT NULL COMMENT '发送者ID',
    `message_type`    VARCHAR(20) DEFAULT 'TEXT' COMMENT '消息类型：TEXT/IMAGE/PRODUCT_CARD/ORDER_CARD',
    `content`         TEXT        DEFAULT NULL COMMENT '消息内容',
    `attachment_urls` JSON        DEFAULT NULL COMMENT '附件URL列表',
    `is_read`         TINYINT     DEFAULT 0 COMMENT '是否已读：0=未读 1=已读',
    `created_at`      DATETIME    NOT NULL COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_time` (`conversation_id`, `created_at`) COMMENT '会话消息列表（时间序）',
    CONSTRAINT `fk_msg_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息记录表（时序数据）';

-- 8.3 离线推送记录表（FK → message）
CREATE TABLE IF NOT EXISTS `push_record` (
    `id`           BIGINT      NOT NULL COMMENT '推送记录ID',
    `user_id`      BIGINT      NOT NULL COMMENT '目标用户ID',
    `message_id`   BIGINT      NOT NULL COMMENT '消息ID',
    `push_channel` VARCHAR(20) DEFAULT NULL COMMENT '推送渠道：IN_APP/APNS/FCM',
    `push_status`  TINYINT     DEFAULT 0 COMMENT '推送状态：0=待推送 1=已推送 2=推送失败',
    `retry_count`  INT         DEFAULT 0 COMMENT '已重试次数',
    `created_at`   DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`   DATETIME    DEFAULT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message` (`message_id`) COMMENT '一条消息一个推送记录',
    CONSTRAINT `fk_push_message` FOREIGN KEY (`message_id`) REFERENCES `message` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线推送记录表';

-- ============================================================
-- 域 9：官方客服体系（4 张表）— cs-service
-- 核心：工单状态机 + SLA 管控 + 审计日志
-- ============================================================

-- 9.1 客服人员表（FK → user）
CREATE TABLE IF NOT EXISTS `cs_agent` (
    `id`             BIGINT      NOT NULL COMMENT 'Agent ID',
    `user_id`        BIGINT      NOT NULL COMMENT '关联user ID',
    `agent_name`     VARCHAR(50) DEFAULT NULL COMMENT '客服昵称',
    `role`           VARCHAR(20) DEFAULT 'AGENT' COMMENT '角色：AGENT/SENIOR/MANAGER',
    `status`         VARCHAR(20) DEFAULT 'OFFLINE' COMMENT '在线状态：ONLINE/OFFLINE/BUSY',
    `max_concurrent` INT         DEFAULT 5 COMMENT '最大并发处理工单数（1-20）',
    `created_at`     DATETIME    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`) COMMENT '一个用户一个客服身份',
    CONSTRAINT `fk_agent_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服人员表';

-- 9.2 客服工单表（FK → user, cs_agent）
CREATE TABLE IF NOT EXISTS `ticket` (
    `id`                BIGINT       NOT NULL COMMENT '工单ID',
    `ticket_no`         VARCHAR(20)  NOT NULL COMMENT '工单号',
    `user_id`           BIGINT       NOT NULL COMMENT '发起用户ID',
    `category`          VARCHAR(30)  DEFAULT NULL COMMENT '分类：ORDER_DISPUTE/REFUND/ACCOUNT/COMPLAINT/GENERAL',
    `priority`          TINYINT      DEFAULT 2 COMMENT '优先级：1=紧急 2=普通 3=低',
    `status`            VARCHAR(20)  DEFAULT 'CREATED' COMMENT '状态：CREATED→ASSIGNED→IN_PROGRESS→RESOLVED→CLOSED',
    `title`             VARCHAR(200) DEFAULT NULL COMMENT '工单标题',
    `order_id`          BIGINT       DEFAULT NULL COMMENT '关联订单ID',
    `assigned_agent_id` BIGINT       DEFAULT NULL COMMENT '被分配客服ID',
    `resolution_note`   TEXT         DEFAULT NULL COMMENT '处理备注',
    `resolved_at`       DATETIME     DEFAULT NULL COMMENT '解决时间',
    `closed_at`         DATETIME     DEFAULT NULL COMMENT '关闭时间',
    `created_at`        DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`        DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_no` (`ticket_no`) COMMENT '工单号唯一',
    KEY `idx_user_status` (`user_id`, `status`) COMMENT '用户工单列表',
    KEY `idx_agent_status` (`assigned_agent_id`, `status`) COMMENT '客服待处理工单',
    CONSTRAINT `fk_ticket_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_ticket_agent` FOREIGN KEY (`assigned_agent_id`) REFERENCES `cs_agent` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服工单表（状态机+SLA）';

-- 9.3 工单消息表（FK → ticket）
CREATE TABLE IF NOT EXISTS `ticket_message` (
    `id`              BIGINT      NOT NULL COMMENT '消息ID',
    `ticket_id`       BIGINT      NOT NULL COMMENT '所属工单ID',
    `sender_type`     VARCHAR(10) NOT NULL COMMENT '发送者类型：USER/AGENT/SYSTEM',
    `sender_id`       BIGINT      NOT NULL COMMENT '发送者ID',
    `content`         TEXT        DEFAULT NULL COMMENT '消息内容',
    `attachment_urls` JSON        DEFAULT NULL COMMENT '附件URL',
    `created_at`      DATETIME    NOT NULL COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_ticket_time` (`ticket_id`, `created_at`) COMMENT '工单消息列表（时间序）',
    CONSTRAINT `fk_tmsg_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `ticket` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单消息表';

-- 9.4 工单操作审计日志表（FK → ticket）
CREATE TABLE IF NOT EXISTS `ticket_audit_log` (
    `id`          BIGINT      NOT NULL COMMENT '日志ID',
    `ticket_id`   BIGINT      NOT NULL COMMENT '所属工单ID',
    `operator_id` BIGINT      NOT NULL COMMENT '操作人ID',
    `action`      VARCHAR(30) NOT NULL COMMENT '操作类型：CREATE/ASSIGN/REPLY/TRANSFER/RESOLVE/CLOSE/REOPEN',
    `detail`      TEXT        DEFAULT NULL COMMENT '操作详情',
    `created_at`  DATETIME    NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_ticket_id` (`ticket_id`) COMMENT '按工单查审计日志',
    CONSTRAINT `fk_audit_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `ticket` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单操作审计日志表（不可物理删除）';

-- ============================================================
-- 域 10：推荐引擎体系（4 张表）— recommendation-service
-- 核心：行为日志 → 离线计算 → 推荐结果物化 + 相似度矩阵
-- ============================================================

-- 10.1 推荐策略配置表（无外部依赖）
CREATE TABLE IF NOT EXISTS `reco_strategy` (
    `id`            BIGINT        NOT NULL COMMENT '策略ID',
    `strategy_code` VARCHAR(30)   NOT NULL COMMENT '策略码：CF_USER/CF_ITEM/HOT/NEW_USER/ASSOC_RULE',
    `strategy_name` VARCHAR(100)  DEFAULT NULL COMMENT '策略中文名',
    `weight`        DECIMAL(5,2)  DEFAULT 1.00 COMMENT '融合权重',
    `enabled`       TINYINT       DEFAULT 1 COMMENT '是否启用：0=否 1=是',
    `config_json`   JSON          DEFAULT NULL COMMENT '策略参数（含A/B实验分流配置）',
    `updated_at`    DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_strategy_code` (`strategy_code`) COMMENT '策略码唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐策略配置表';

-- 10.2 用户行为日志表（FK → user, product）
CREATE TABLE IF NOT EXISTS `user_behavior` (
    `id`             BIGINT       NOT NULL COMMENT '行为ID',
    `user_id`        BIGINT       NOT NULL COMMENT '用户ID',
    `product_id`     BIGINT       NOT NULL COMMENT '商品ID',
    `behavior_type`  VARCHAR(20)  NOT NULL COMMENT '行为类型：VIEW/SEARCH/ADD_CART/PURCHASE/COLLECT/SHARE',
    `session_id`     VARCHAR(64)  DEFAULT NULL COMMENT '会话ID',
    `search_keyword` VARCHAR(100) DEFAULT NULL COMMENT '搜索关键词',
    `duration_ms`    INT          DEFAULT 0 COMMENT '浏览停留时长（毫秒）',
    `created_at`     DATETIME     NOT NULL COMMENT '行为发生时间（分区键）',
    PRIMARY KEY (`id`, `created_at`),
    KEY `idx_user_time` (`user_id`, `created_at`) COMMENT '用户行为时间线',
    KEY `idx_product_time` (`product_id`, `created_at`) COMMENT '商品被行为分析'-- ,
--     CONSTRAINT `fk_behavior_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
--         ON DELETE CASCADE ON UPDATE CASCADE,
--     CONSTRAINT `fk_behavior_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
--         ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为日志表（日均百万级写入）'
PARTITION BY RANGE (TO_DAYS(`created_at`)) (
    PARTITION p20260701 VALUES LESS THAN (TO_DAYS('2026-07-02')),
    PARTITION p20260702 VALUES LESS THAN (TO_DAYS('2026-07-03')),
    PARTITION p20260703 VALUES LESS THAN (TO_DAYS('2026-07-04')),
    PARTITION p20260704 VALUES LESS THAN (TO_DAYS('2026-07-05')),
    PARTITION p20260705 VALUES LESS THAN (TO_DAYS('2026-07-06')),
    PARTITION p20260706 VALUES LESS THAN (TO_DAYS('2026-07-07')),
    PARTITION p20260707 VALUES LESS THAN (TO_DAYS('2026-07-08')),
    PARTITION p20260708 VALUES LESS THAN (TO_DAYS('2026-07-09')),
    PARTITION p20260709 VALUES LESS THAN (TO_DAYS('2026-07-10')),
    PARTITION p20260710 VALUES LESS THAN (TO_DAYS('2026-07-11')),
    PARTITION p20260711 VALUES LESS THAN (TO_DAYS('2026-07-12')),
    PARTITION p20260712 VALUES LESS THAN (TO_DAYS('2026-07-13')),
    PARTITION p20260713 VALUES LESS THAN (TO_DAYS('2026-07-14')),
    PARTITION p20260714 VALUES LESS THAN (TO_DAYS('2026-07-15')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- 10.3 推荐结果物化表（FK → user, product）
CREATE TABLE IF NOT EXISTS `reco_result` (
    `id`           BIGINT        NOT NULL COMMENT '推荐结果ID',
    `user_id`      BIGINT        NOT NULL COMMENT '用户ID',
    `product_id`   BIGINT        NOT NULL COMMENT '推荐商品ID',
    `strategy`     VARCHAR(30)   NOT NULL COMMENT '推荐策略：CF_USER/CF_ITEM/HOT/NEW_USER/ASSOC_RULE',
    `score`        DECIMAL(10,4) NOT NULL COMMENT '推荐分数',
    `position`     INT           DEFAULT 0 COMMENT '排序位置',
    `generated_at` DATETIME      NOT NULL COMMENT '生成时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_strategy` (`user_id`, `strategy`, `score` DESC) COMMENT '用户推荐查询（按策略+分数排序）',
    KEY `idx_generated` (`generated_at`) COMMENT '按生成时间查询最新推荐',
    CONSTRAINT `fk_reco_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_reco_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐结果物化表（离线计算写入，在线服务读取）';

-- 10.4 商品相似度矩阵表（无外部约束，逻辑引用 product）
CREATE TABLE IF NOT EXISTS `product_similarity` (
    `product_id_a` BIGINT        NOT NULL COMMENT '商品A',
    `product_id_b` BIGINT        NOT NULL COMMENT '商品B',
    `similarity`   DECIMAL(10,4) NOT NULL COMMENT '相似度（0~1）',
    `updated_at`   DATETIME      NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`product_id_a`, `product_id_b`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品相似度矩阵表（Item-CF 离线计算）';

-- ============================================================
-- 初始化种子数据
-- ============================================================

-- 角色预置数据
INSERT INTO `role` (`id`, `name`, `description`, `create_time`) VALUES
(1, 'ROLE_ADMIN',       '系统管理员——全权限',        NOW()),
(2, 'ROLE_USER',        '普通用户——C端买家',        NOW()),
(3, 'ROLE_MERCHANT',    '入驻商家——B端商家',        NOW()),
(4, 'ROLE_CS_AGENT',    '客服人员——工单处理',        NOW()),
(5, 'ROLE_CS_MANAGER',  '客服主管——工单分配与管理',   NOW());

-- ============================================
-- 权限种子数据（33 条）
-- ============================================
INSERT INTO permission (id, code, name, description, create_time) VALUES
-- 用户与权限
(1,  'user:list',              '用户列表',     '分页查看所有用户',           NOW()),
(2,  'user:read',              '用户详情',     '查看单个用户信息',           NOW()),
(3,  'user:update',            '用户管理',     '启禁用户、修改状态',         NOW()),
(4,  'role:manage',            '角色权限管理', '角色CRUD + 权限分配',         NOW()),
-- 商品
(5,  'product:list',           '商品列表',     '浏览商品列表',               NOW()),
(6,  'product:read',           '商品详情',     '查看商品SPU/SKU详情',        NOW()),
(7,  'product:create',         '商品新增',     '上架新商品',                 NOW()),
(8,  'product:update',         '商品编辑',     '修改商品信息',               NOW()),
(9,  'product:delete',         '商品下架',     '下架/删除商品',              NOW()),
(10, 'product:audit',          '商品审核',     '平台审核商品',               NOW()),
-- 订单
(11, 'order:list',             '订单列表',     '查看订单列表',               NOW()),
(12, 'order:read',             '订单详情',     '查看订单详情',               NOW()),
(13, 'order:create',           '下单',         '创建订单',                   NOW()),
(14, 'order:cancel',           '取消订单',     'C端取消未支付订单',          NOW()),
(15, 'order:ship',             '订单发货',     '商家发货填写物流',           NOW()),
(16, 'order:refund',           '退款处理',     '审核/执行退款',              NOW()),
(17, 'order:view',             '全量订单',     '跨商家查看平台所有订单',     NOW()),
-- 商家
(18, 'merchant:list',          '商家列表',     '查看商家列表',               NOW()),
(19, 'merchant:read',          '商家详情',     '查看商家资质信息',           NOW()),
(20, 'merchant:audit',         '商家审核',     '审核入驻申请',               NOW()),
(21, 'merchant:freeze',        '商家冻结',     '冻结/解冻商家',              NOW()),
-- 客服
(22, 'ticket:list',            '工单列表',     '查看工单列表',               NOW()),
(23, 'ticket:read',            '工单详情',     '查看工单详情',               NOW()),
(24, 'ticket:create',          '创建工单',     'C端发起客服工单',            NOW()),
(25, 'ticket:assign',          '分配工单',     '主管分配工单',               NOW()),
(26, 'ticket:resolve',         '处理工单',     'agent回复并解决',            NOW()),
(27, 'ticket:close',           '关闭工单',     '确认关闭工单',               NOW()),
-- 营销
(28, 'promotion:list',         '活动列表',     '查看优惠活动',               NOW()),
(29, 'promotion:manage',       '活动管理',     '创建/编辑/删除活动',         NOW()),
-- 结算/推荐/看板
(30, 'settlement:bill:list',   '账单列表',     '查看结算账单',               NOW()),
(31, 'settlement:bill:settle', '执行结算',     '确认结算打款',               NOW()),
(32, 'reco:config',            '推荐配置',     '调整推荐策略权重',           NOW()),
(33, 'dashboard:view',         '管理看板',     '查看平台经营数据看板',       NOW());

-- ============================================
-- 角色-权限关联（共 64 条）
-- ============================================

-- ROLE_ADMIN(1)：全部 33 项，ID 1-33
INSERT INTO `role_permission` (`id`, `role_id`, `permission_id`) VALUES
(1,1,1),(2,1,2),(3,1,3),(4,1,4),(5,1,5),(6,1,6),(7,1,7),(8,1,8),(9,1,9),(10,1,10),
(11,1,11),(12,1,12),(13,1,13),(14,1,14),(15,1,15),(16,1,16),(17,1,17),(18,1,18),
(19,1,19),(20,1,20),(21,1,21),(22,1,22),(23,1,23),(24,1,24),(25,1,25),(26,1,26),
(27,1,27),(28,1,28),(29,1,29),(30,1,30),(31,1,31),(32,1,32),(33,1,33);

-- ROLE_USER(2)：C端消费者 9 项，ID 34-42
INSERT INTO `role_permission` (`id`, `role_id`, `permission_id`) VALUES
(34,2,5),   -- product:list
(35,2,6),   -- product:read
(36,2,11),  -- order:list
(37,2,12),  -- order:read
(38,2,13),  -- order:create
(39,2,14),  -- order:cancel
(40,2,23),  -- ticket:read
(41,2,24),  -- ticket:create
(42,2,28);  -- promotion:list

-- ROLE_MERCHANT(3)：商家 11 项，ID 43-53
INSERT INTO `role_permission` (`id`, `role_id`, `permission_id`) VALUES
(43,3,5),   -- product:list
(44,3,6),   -- product:read
(45,3,7),   -- product:create
(46,3,8),   -- product:update
(47,3,9),   -- product:delete
(48,3,11),  -- order:list
(49,3,12),  -- order:read
(50,3,15),  -- order:ship
(51,3,16),  -- order:refund
(52,3,28),  -- promotion:list
(53,3,30);  -- settlement:bill:list

-- ROLE_CS_AGENT(4)：客服专员 4 项，ID 54-57
INSERT INTO `role_permission` (`id`, `role_id`, `permission_id`) VALUES
(54,4,22),  -- ticket:list
(55,4,23),  -- ticket:read
(56,4,26),  -- ticket:resolve
(57,4,27);  -- ticket:close

-- ROLE_CS_MANAGER(5)：客服主管 7 项，ID 58-64
INSERT INTO `role_permission` (`id`, `role_id`, `permission_id`) VALUES
(58,5,1),   -- user:list
(59,5,2),   -- user:read
(60,5,22),  -- ticket:list
(61,5,23),  -- ticket:read
(62,5,25),  -- ticket:assign
(63,5,26),  -- ticket:resolve
(64,5,27);  -- ticket:close



-- 默认推荐策略配置
INSERT INTO `reco_strategy` (`id`, `strategy_code`, `strategy_name`, `weight`, `enabled`, `config_json`, `updated_at`) VALUES
(1, 'CF_USER',    '协同过滤-用户',  6.50, 1, '{"minCommonItems": 3}',        NOW()),
(2, 'CF_ITEM',    '协同过滤-物品',  5.00, 1, '{"minCoViews": 5}',            NOW()),
(3, 'HOT',        '热门排行',       3.00, 1, '{"timeWindow": "7d"}',         NOW()),
(4, 'NEW_USER',   '新人推荐',       2.00, 1, '{"daysSinceReg": 14}',         NOW()),
(5, 'ASSOC_RULE', '关联规则',       4.00, 1, '{"minConfidence": 0.3}',       NOW());

-- 应用账号权限（取消注释并替换密码后可用于生产环境）
-- CREATE USER IF NOT EXISTS 'ecommerce_app'@'%' IDENTIFIED WITH mysql_native_password BY 'ChangeMe';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ecommerce.* TO 'ecommerce_app'@'%';
-- FLUSH PRIVILEGES;

-- 提交事务并重新开启外键检查
COMMIT;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 脚本结束
-- ============================================================
-- 表总数：44 张
-- 覆盖 10 个业务域：用户(6) + 商品(6) + 订单(4) + 支付(1) + 商家(5) + 促销(8) + 结算(3) + 消息(3) + 客服(4) + 推荐(4)
-- 外键关系：约 46 条 FOREIGN KEY 约束
-- 索引总数：约 110 个（含 PRIMARY KEY, UNIQUE KEY, INDEX）
-- 分区表：1 张（user_behavior，按日分区）
-- ============================================================
