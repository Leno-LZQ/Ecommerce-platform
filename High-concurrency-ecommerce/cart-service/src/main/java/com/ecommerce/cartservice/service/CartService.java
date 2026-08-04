package com.ecommerce.cartservice.service;

import com.alibaba.fastjson2.JSON;
import com.ecommerce.cartservice.dto.AddToCartRequest;
import com.ecommerce.cartservice.dto.CartItemVO;
import com.ecommerce.cartservice.dto.CartVO;
import com.ecommerce.cartservice.dto.MerchantCartGroupVO;
import com.ecommerce.client.InventoryClient;
import com.ecommerce.client.ProductClient;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.dto.product.SkuDetailDTO;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CartService {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ProductClient productClient;
    @Autowired private InventoryClient inventoryClient;

    private static final String CART_KEY_PREFIX = "cart:user:";
    private static final int MAX_CART_ITEMS = 100;         // 购物车最多 100 个 SKU
    private static final int MAX_QUANTITY_PER_SKU = 999;   // 单个 SKU 最多 999 件

    // ==================== 查 询 ====================

    public CartVO getCart(Long userId) {
        String key = CART_KEY_PREFIX + userId;
        Map<Object, Object> items = redisTemplate.opsForHash().entries(key);

        if (items.isEmpty()) {
            return CartVO.builder()
                    .userId(userId)
                    .merchantGroups(Collections.emptyList())
                    .totalQuantity(0)
                    .selectedQuantity(0)
                    .totalPrice(BigDecimal.ZERO)
                    .selectedPrice(BigDecimal.ZERO)
                    .promotions(Collections.emptyList())
                    .discountAmount(BigDecimal.ZERO)
                    .payableAmount(BigDecimal.ZERO)
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        List<CartItemVO> cartItems = items.values().stream()
                .map(this::convertToCartItem)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, List<CartItemVO>> groupedByMerchant = cartItems.stream()
                .collect(Collectors.groupingBy(CartItemVO::getMerchantId));

        return buildCartVO(userId, cartItems, groupedByMerchant);
    }

    // ==================== 加 购 ====================

    public CartVO addItem(Long userId, AddToCartRequest request) {
        Long skuId = request.getSkuId();
        int addQuantity = request.getQuantity();
        String key = CART_KEY_PREFIX + userId;

        // ① 查询 SKU 详情（内部调用 product-service）
        Result<SkuDetailDTO> result = productClient.getSkuDetail(skuId);
        if (result == null || result.getData() == null) {
            throw new BusinessException(ErrorCode.SKU_NOT_FOUND);
        }
        SkuDetailDTO skuDetail = result.getData();

        // ② 库存校验：加购数量不能超过可用库存
        Result<Integer> stockResult = inventoryClient.getStock(skuId);
        Integer availableStock = (stockResult != null && stockResult.getData() != null)
                ? stockResult.getData() : 0;
        if (addQuantity > availableStock) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }

        // ③ 查询该 SKU 是否已在购物车中，合并数量
        Object existingValue = redisTemplate.opsForHash().get(key, skuId.toString());
        CartItemVO existingItem = (existingValue != null) ? convertToCartItem(existingValue) : null;

        int finalQuantity;
        boolean isNew = (existingItem == null);

        if (isNew) {
            // 新 SKU：检查购物车总数上限
            Long currentSize = redisTemplate.opsForHash().size(key);
            if (currentSize != null && currentSize >= MAX_CART_ITEMS) {
                throw new BusinessException(ErrorCode.CART_ITEM_LIMIT_EXCEEDED);
            }
            finalQuantity = addQuantity;
        } else {
            // 已有 SKU：累加数量，上限校验
            finalQuantity = existingItem.getQuantity() + addQuantity;
            if (finalQuantity > availableStock) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
            }
            if (finalQuantity > MAX_QUANTITY_PER_SKU) {
                finalQuantity = MAX_QUANTITY_PER_SKU;
            }
        }

        // ④ 构建 CartItemVO（快照价格）
        CartItemVO newItem = CartItemVO.builder()
                .skuId(skuId)
                .productId(skuDetail.getProductId())
                .productName(skuDetail.getProductName())
                .price(skuDetail.getPrice())       // 快照价格
                .image(skuDetail.getImage())
                .quantity(finalQuantity)
                .checked(true)                     // 新加购默认勾选
                .snapshotTime(LocalDateTime.now())
                .merchantId(skuDetail.getMerchantId())
                .build();

        // ⑤ 写入 Redis Hash
        redisTemplate.opsForHash().put(key, skuId.toString(), JSON.toJSONString(newItem));

        log.info("加购成功: userId={}, skuId={}, quantity={}, isNew={}", userId, skuId, finalQuantity, isNew);

        // ⑥ 返回全量购物车
        return getCart(userId);
    }

    // ==================== 更新数量 ====================

    public CartVO updateQuantity(Long userId, Long skuId, Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT); // quantity 至少为 1
        }
        if (quantity > MAX_QUANTITY_PER_SKU) {
            quantity = MAX_QUANTITY_PER_SKU;
        }
        String key = CART_KEY_PREFIX + userId;

        // ① 检查 SKU 是否在购物车中
        Object existingValue = redisTemplate.opsForHash().get(key, skuId.toString());
        if (existingValue == null) {
            throw new BusinessException(ErrorCode.CART_NOT_FOUND); // 该 SKU 不在购物车中
        }

        // ② 库存校验
        Result<Integer> stockResult = inventoryClient.getStock(skuId);
        Integer availableStock = (stockResult != null && stockResult.getData() != null)
                ? stockResult.getData() : 0;
        if (quantity > availableStock) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }

        // ③ 更新
        CartItemVO item = convertToCartItem(existingValue);
        item.setQuantity(quantity);
        item.setSnapshotTime(LocalDateTime.now());
        redisTemplate.opsForHash().put(key, skuId.toString(), JSON.toJSONString(item));

        log.info("更新数量成功: userId={}, skuId={}, quantity={}", userId, skuId, quantity);
        return getCart(userId);
    }

    // ==================== 删除单品 ====================

    public CartVO removeItem(Long userId, Long skuId) {
        String key = CART_KEY_PREFIX + userId;
        redisTemplate.opsForHash().delete(key, skuId.toString());
        log.info("删除购物车单品: userId={}, skuId={}", userId, skuId);
        return getCart(userId);
    }

    // ==================== 勾选 / 取消勾选 ====================

    public CartVO checkItem(Long userId, Long skuId, Boolean checked) {
        String key = CART_KEY_PREFIX + userId;
        Object existingValue = redisTemplate.opsForHash().get(key, skuId.toString());
        if (existingValue == null) {
            throw new BusinessException(ErrorCode.CART_NOT_FOUND);
        };

        CartItemVO item = convertToCartItem(existingValue);
        item.setChecked(checked != null && checked);
        redisTemplate.opsForHash().put(key, skuId.toString(), JSON.toJSONString(item));

        return getCart(userId);
    }

    // ==================== 全选 / 取消全选 ====================

    public CartVO checkAll(Long userId, Boolean checked) {
        String key = CART_KEY_PREFIX + userId;
        Map<Object, Object> items = redisTemplate.opsForHash().entries(key);

        if (items.isEmpty()) {
            return getCart(userId);
        }

        boolean targetChecked = checked != null && checked;
        items.forEach((field, value) -> {
            CartItemVO item = convertToCartItem(value);
            if (item != null) {
                item.setChecked(targetChecked);
                redisTemplate.opsForHash().put(key, field.toString(), JSON.toJSONString(item));
            }
        });

        return getCart(userId);
    }

    // ==================== 清空购物车 ====================

    public void clearCart(Long userId) {
        String key = CART_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        log.info("清空购物车: userId={}", userId);
    }

    // ==================== 内部工具方法 ====================

    private CartItemVO convertToCartItem(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.parseObject(value.toString(), CartItemVO.class);
        } catch (Exception e) {
            log.error("购物车数据解析失败: {}", value, e);
            throw new BusinessException(ErrorCode.CART_PARSE_FALISE);
        }
    }

    private CartVO buildCartVO(Long userId,
                               List<CartItemVO> cartItems,
                               Map<Long, List<CartItemVO>> groupedByMerchant) {

        // ① 逐商家构建分组 VO
        List<MerchantCartGroupVO> merchantGroups = groupedByMerchant.entrySet().stream()
                .map(entry -> buildMerchantGroup(entry.getKey(), entry.getValue()))
                .toList();

        // ② 全量汇总
        int totalQuantity = cartItems.stream()
                .mapToInt(CartItemVO::getQuantity)
                .sum();

        int selectedQuantity = cartItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .mapToInt(CartItemVO::getQuantity)
                .sum();

        BigDecimal totalPrice = cartItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal selectedPrice = cartItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ③ 优惠汇总（商家级累加）
        BigDecimal discountAmount = merchantGroups.stream()
                .map(g -> g.getDiscountAmount() != null ? g.getDiscountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ④ 应付金额（不能为负）
        BigDecimal payableAmount = selectedPrice.subtract(discountAmount);
        if (payableAmount.compareTo(BigDecimal.ZERO) < 0) {
            payableAmount = BigDecimal.ZERO;
        }

        return CartVO.builder()
                .userId(userId)
                .merchantGroups(merchantGroups)
                .totalQuantity(totalQuantity)
                .selectedQuantity(selectedQuantity)
                .totalPrice(totalPrice)
                .selectedPrice(selectedPrice)
                .promotions(Collections.emptyList())
                .discountAmount(discountAmount)
                .payableAmount(payableAmount)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private MerchantCartGroupVO buildMerchantGroup(Long merchantId, List<CartItemVO> items) {
        int itemCount = items.size();
        int selectedCount = (int) items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .count();

        BigDecimal totalPrice = items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal selectedPrice = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return MerchantCartGroupVO.builder()
                .merchantId(merchantId)
                .merchantName(null)
                .items(items)
                .itemCount(itemCount)
                .selectedCount(selectedCount)
                .totalPrice(totalPrice)
                .selectedPrice(selectedPrice)
                .promotions(Collections.emptyList())
                .discountAmount(BigDecimal.ZERO)
                .build();
    }
}
