package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.CategoryRequest;
import com.ecommerce.productservice.dto.CategoryResponse;
import com.ecommerce.productservice.service.CategoryService;
import com.ecommerce.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    public Result<List<CategoryResponse>> getTree() {
        return Result.success(categoryService.getTree());
    }

    @GetMapping("/{id}/children")
    public Result<List<CategoryResponse>> getChildren(@PathVariable("id") Long parentId){
        return Result.success(categoryService.getChildren(parentId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('category:create')")
    public Result<CategoryResponse> create(@RequestBody @Valid CategoryRequest request){
        return Result.success(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('category:update')")
    public Result<CategoryResponse> update(@PathVariable Long id, @RequestBody @Valid CategoryRequest request){
        return Result.success(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('category:delete')")
    public Result<String> delete(@PathVariable Long id){
        categoryService.delete(id);
        return Result.success("删除成功");
    }
}
