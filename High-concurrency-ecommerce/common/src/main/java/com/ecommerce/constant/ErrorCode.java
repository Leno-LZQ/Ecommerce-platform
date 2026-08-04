package com.ecommerce.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ==================== 用户模块 10000~19999 ====================
    // 基础账户 10001~10009
    USER_NOT_FOUND(10001, "用户不存在"),
    PASSWORD_ERROR(10002, "密码错误"),
    USERNAME_DUPLICATE(10003, "用户名已被注册"),
    PHONE_DUPLICATE(10004, "手机号已被注册"),
    ACCOUNT_DISABLED(10005, "账号已被禁用"),
    ACCOUNT_LOCKED(10006, "账户已临时锁定，请15分钟后重试"),

    // 令牌认证 10010~10019
    TOKEN_EXPIRED(10010, "登录已过期，请重新登录"),
    TOKEN_BLACKLISTED(10011, "Token 已失效"),
    TOKEN_INVALID(10012, "令牌无效"),
    TOKEN_REVOKED(10013, "令牌已失效，请重新登录"),



    // 登录认证 10020~10029
    PHONE_ERROR(10020, "手机号错误"),

    // 短信验证码 10030~10039
    SMS_SEND_TOO_FAST(10030, "请60秒后再试"),
    SMS_DAILY_LIMIT_EXCEEDED(10031, "今日发送次数已达上限"),
    SMS_CODE_ERROR(10032, "验证码错误"),
    SMS_CODE_EXPIRED(10033, "验证码已过期"),

    // 密码修改 10040~10049
    PASSWORD_TOO_WEAK(10040, "密码强度不足：至少8位，含大小写字母和数字中的至少两种"),
    PASSWORD_SAME_AS_OLD(10041, "新密码不能与旧密码相同"),
    PASSWORD_UPDATE_FAILED(10042, "密码修改失败，请稍后重试"),
    PASSWORD_RESET_FAILED(10043, "密码重置失败，请稍后重试"),

    // 更换手机号 10050~10059
    CHANGE_PHONE_FAILED(10050, "更换手机号失败，请稍后重试"),
    CHANGE_PHONE_TOKEN_INVALID(10051, "换绑凭证无效，请重新验证密码"),
    CHANGE_PHONE_TOKEN_EXPIRED(10052, "换绑凭证已过期，请重新验证密码"),
    CHANGE_PHONE_TOO_FREQUENT(10053, "换绑操作过于频繁，请72小时后再试"),
    PHONE_ALREADY_EXISTS(10054, "该手机号已被绑定"),
    PHONE_SAME_AS_CURRENT(10055, "新手机号与当前一致"),

    // 图形验证码 10060~10069
    CAPTCHA_ERROR(10060, "图形验证码错误"),
    CAPTCHA_EXPIRED(10061, "图形验证码已过期"),

    // RBAC 角色/权限管理 10080~10099
    ROLE_NAME_DUPLICATE(10080, "角色名已存在"),
    ROLE_PRESET_CANNOT_DELETE(10081, "预置角色不允许删除"),
    ROLE_NOT_FOUND(10082, "角色不存在"),
    USER_MUST_HAVE_ROLE(10083, "用户至少保留一个角色"),
    ROLE_ALREADY_ASSIGNED(10084, "用户已拥有该角色"),

    // 地址管理 10100~10109
    ADDRESS_LIMIT_EXCEEDED(10100, "收货地址已达上限（20个）"),
    ADDRESS_NOT_FOUND(10101, "收货地址不存在"),

    // ==================== 商品模块 20000~29999 ====================
    PRODUCT_NOT_FOUND(20001, "商品不存在"),
    STOCK_INSUFFICIENT(20002, "库存不足"),
    PRODUCT_OFF_SHELF(20003, "商品已下架"),
    PRODUCT_IMAGE_TOO_MANY(20004, "商品图片数量超过限制"),
    PRODUCT_SKU_EMPTY(20005, "商品SKU为空"),
    PRODUCT_SKU_TOO_MANY(20006, "商品SKU数量超过限制"),
    SKU_ATTRS_DUPLICATE(20007, "商品SKU属性重复"),
    PRODUCT_CATEGORY_NOT_EXIST(20008,"商品分类不存在"),
    PRODUCT_NO_PERMISSION(20009,"商品无权限"),
    //商品分类管理 20010~20019
    CATEGORY_NAME_DUPLICATE(20010, "分类名已存在"),
    CATEGORY_PRESET_CANNOT_DELETE(20011, "预置分类不允许删除"),
    CATEGORY_NOT_FOUND(20012, "分类不存在"),
    CATEGORY_PARENT_NOT_FOUND(20013, "父分类不存在"),
    CATEGORY_HAS_CHILDREN(20014, "该分类下有子分类，无法删除"),
    PRODUCT_CANNOT_EDIT_AUDITED(20015,"商品已审核中"),
    SKU_NOT_FOUND(20016,"sku不存在"),
    PRODUCT_NOT_AUDITED(20017,"商品审核中"),

    NOTIFY_ALREADY_SUBSCRIBED(20020, "已订阅该SKU的到货通知，请勿重复订阅"),
    NOTIFY_SKU_HAS_STOCK(20021, "该SKU当前有货，无需订阅到货通知"),

    CART_NOT_FOUND(20030,"购物车未找到"),
    CART_PARSE_FALISE(20031,"购物车解析失败"),
    CART_ITEM_LIMIT_EXCEEDED(20032,"购物车商品数量已达上限"),

    // ==================== 订单模块 30000~39999 ====================
    ORDER_NOT_FOUND(30001, "订单不存在"),
    ORDER_DUPLICATE(30002, "请勿重复提交订单"),
    ORDER_STATUS_ERROR(30003, "订单状态异常"),
    ORDER_PRICE_MISMATCH(30004, "订单金额不匹配"),
    ORDER_LIMIT_EXCEEDED(30005, "操作太频繁，请稍后重试"),

    INVENTORY_SHORTAGE(30006,"库存不足"),
    PROMOTION_LOCK_FAILED(30007,"促销资源锁定失败"),
    PAYMENT_CREATE_FAILED(30008,"支付失败"),

    // ==================== 支付模块 40000~49999 ====================
    PAY_DUPLICATE(40001, "已有进行中的支付"),
    PAY_FAILED(40002, "支付失败"),
    PAY_TIMEOUT(40003, "支付超时"),

    // ==================== 系统通用 50000~59999 ====================
    SYSTEM_ERROR(50000, "服务器内部错误，请稍后重试"),
    RATE_LIMITED(50001, "系统繁忙，请稍后重试（限流中）"),
    SERVICE_UNAVAILABLE(50002, "服务暂不可用"),

    // ==================== 商家模块 44000~44999 ====================
    MERCHANT_NOT_FOUND(44001, "商家不存在"),
    MERCHANT_ALREADY_EXISTS(44002, "该用户已入驻"),
    MERCHANT_STATUS_INVALID(44003, "商家状态不允许此操作"),
    MERCHANT_AUDIT_STATUS_INVALID(44004, "非待审核状态不可审核"),
    SUB_ACCOUNT_DUPLICATE(44005, "同一用户只能有一个子账号"),
    SUB_ACCOUNT_LIMIT_EXCEEDED(44006, "子账号数量已达上限（30个）"),
    SUB_ACCOUNT_ROLE_INVALID(44007, "子账号角色不合法"),
    SUB_ACCOUNT_ALREADY_EXISTS(44008, "该用户已是子账号"),
    SUB_ACCOUNT_NOT_FOUND(44009, "子账号不存在"),
    MERCHANT_HAS_PENDING_ORDERS(44010, "有未完成订单，不可注销"),
    MERCHANT_SELF_AS_SUB_ACCOUNT(44011, "商家本人不可添加为子账号"),

    // ==================== 评价模块 60000~69999 ====================
    REVIEW_DUPLICATE(60001, "您已评价过该商品"),
    REVIEW_ORDER_NOT_COMPLETED(60002, "仅已完成订单可评价"),
    REVIEW_SCORE_INVALID(60003, "评分须在 1~5 之间"),

    // ==================== 消息通信 70000~79999 ====================
    NOTIFICATION_SEND_FAILED(70001, "消息推送失败"),
    CONVERSATION_NOT_FOUND(70002, "会话不存在"),
    MESSAGE_SEND_TOO_FAST(70003, "消息发送过于频繁"),
    CONVERSATION_NO_PERMISSION(70004,"无权访问该会话"),
    CONVERSATION_ARCHIVED(70005,"会话已归档"),

    // ==================== 客服工单 80000~89999 ====================
    TICKET_NOT_FOUND(80001, "工单不存在"),
    TICKET_STATUS_ERROR(80002, "工单状态异常"),
    TICKET_SLA_TIMEOUT(80003, "工单处理超时"),
    TICKET_LIMIT_EXCEEDED(80004, "工单创建过于频繁"),

    // ==================== 推荐引擎 90000~99999 ====================
    RECO_NOT_AVAILABLE(90001, "推荐服务暂不可用"),
    RECO_COLD_START(90002, "暂无足够数据为您推荐"),

    // ==================== 优惠/营销 100000~109999 ====================
    COUPON_NOT_FOUND(100001, "优惠券不存在"),
    COUPON_EXPIRED(100002, "优惠券已过期"),
    COUPON_USED(100003, "优惠券已使用"),
    COUPON_STOCK_OUT(100004, "优惠券已领完"),
    COUPON_LIMIT_EXCEEDED(100005, "已达领券上限"),
    PROMOTION_ENDED(100006, "活动已结束"),
    SECKILL_STOCK_OUT(100007, "秒杀商品已售罄"),
    PROMOTION_RULE_CONFLICT(100008, "优惠叠加规则冲突"),
    PROMOTION_BUDGET_NOT_ENOUGH(100009, "活动预算不足"),
    PROMOTION_DAILY_LIMIT(100010, "今日参与次数已达上限"),
    COUPON_THRESHOLD_NOT_MET(100011, "未满足优惠券使用门槛"),
    PROMOTION_LOCK_CONFLICT(100012, "订单优惠已锁定（重复请求）");

    // ==================== 字段 & 构造 ====================
    private final int code;
    private final String message;
}
