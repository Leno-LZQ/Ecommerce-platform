package com.ecommerce.cartservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.cartservice.dto.AddToCartRequest;
import com.ecommerce.cartservice.dto.CartVO;
import com.ecommerce.cartservice.service.CartService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** 查看购物车 */
    @GetMapping
    public Result<CartVO> getCart() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(cartService.getCart(userId));
    }

    /** 加购 */
    @PostMapping("/items")
    public Result<CartVO> addItem(@RequestBody @Valid AddToCartRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(cartService.addItem(userId, request));
    }

    /** 更新单品数量 */
    @PutMapping("/items/{skuId}")
    public Result<CartVO> updateQuantity(@PathVariable Long skuId,
                                         @RequestParam Integer quantity) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(cartService.updateQuantity(userId, skuId, quantity));
    }

    /** 删除购物车单品 */
    @DeleteMapping("/items/{skuId}")
    public Result<CartVO> removeItem(@PathVariable Long skuId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(cartService.removeItem(userId, skuId));
    }

    /** 勾选 / 取消勾选单品 */
    @PutMapping("/items/{skuId}/check")
    public Result<CartVO> checkItem(@PathVariable Long skuId,
                                    @RequestParam Boolean checked) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(cartService.checkItem(userId, skuId, checked));
    }

    /** 全选 / 取消全选 */
    @PutMapping("/check-all")
    public Result<CartVO> checkAll(@RequestParam Boolean checked) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(cartService.checkAll(userId, checked));
    }

    /** 清空购物车 */
    @DeleteMapping
    public Result<Void> clearCart() {
        Long userId = SecurityUtils.getCurrentUserId();
        cartService.clearCart(userId);
        return Result.success();
    }
}
