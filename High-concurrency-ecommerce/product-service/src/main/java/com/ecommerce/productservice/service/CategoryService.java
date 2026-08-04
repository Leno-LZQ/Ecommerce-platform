package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.CategoryResponse;
import com.ecommerce.productservice.dto.CategoryRequest;

import java.util.List;

public interface CategoryService {

    /** 获取完整分类树（仅启用分类） */
    List<CategoryResponse> getTree();

    /** 获取指定分类的直接子分类 */
    List<CategoryResponse> getChildren(Long parentId);

    /** 创建分类，返回创建后的 Response */
    CategoryResponse create(CategoryRequest request);

    /** 更新分类 */
    CategoryResponse update(Long id, CategoryRequest request);

    /** 删除分类 */
    void delete(Long id);
}
