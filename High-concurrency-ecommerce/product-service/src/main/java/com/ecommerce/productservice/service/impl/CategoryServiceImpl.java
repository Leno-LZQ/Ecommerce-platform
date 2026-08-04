package com.ecommerce.productservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.productservice.dto.CategoryRequest;
import com.ecommerce.productservice.dto.CategoryResponse;
import com.ecommerce.productservice.entity.Category;
import com.ecommerce.productservice.mapper.CategoryMapper;
import com.ecommerce.productservice.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;


    @Override
    @Cacheable(value = "categoryTree", cacheManager = "caffeineCacheManager")
    public List<CategoryResponse> getTree() {
        List<Category> all = categoryMapper.selectList(
            new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSortOrder)
        );

        List<Category> roots = all.stream()
            .filter(c -> c.getParentId() == 0L)
            .toList();

        return roots.stream()
            .map(r -> buildTree(r, all))
            .collect(Collectors.toList());
    }

    /** 递归构建子树 */
    private CategoryResponse buildTree(Category parent, List<Category> all) {
        CategoryResponse vo = toTreeResponse(parent);
        List<CategoryResponse> children = all.stream()
            .filter(c -> c.getParentId().equals(parent.getId()))
            .map(c -> buildTree(c, all))
            .collect(Collectors.toList());
        vo.setChildren(children.isEmpty() ? null : children);
        return vo;
    }

    @Override
    public List<CategoryResponse> getChildren(Long parentId) {
        Category parent = categoryMapper.selectById(parentId);
        if(parent == null){
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        List<Category> children = categoryMapper.selectList(
            new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, parentId)
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSortOrder)
        );
        return children.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    public CategoryResponse create(CategoryRequest request) {
        // Step 1：如果 parentId != 0，校验父分类存在且为启用状态，level<3
        int level = 1;
        if (request.getParentId() != 0L) {
            Category parent = categoryMapper.selectById(request.getParentId());
            if (parent == null || parent.getStatus() == 0) {
                throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
            }
            if (parent.getLevel() >= 3) {
                throw new BusinessException(41004, "分类层级最多三级");
            }
            level = parent.getLevel() + 1;
        }
        // Step 2：检查同级同名（同 parentId 下 name 不能重复）
        Long count = categoryMapper.selectCount(
            new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, request.getParentId())
                .eq(Category::getName, request.getName())
        );
        if (count > 0) {
            throw new BusinessException(41005, "同级分类下已有同名分类");
        }
        // Step 3：构建实体并插入
        Category category = new Category();
        category.setParentId(request.getParentId());
        category.setName(request.getName());
        category.setLevel(level);
        category.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        category.setIcon(request.getIcon());
        category.setStatus(1);
        category.setCreateTime(LocalDateTime.now());
        categoryMapper.insert(category);
        log.info("分类创建成功: id={}, name={}, level={}", category.getId(), category.getName(), level);
        return toResponse(category);
    }

    // ==================== update ====================
    @Override
    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        // 仅更新非 null 字段
        if (request.getName() != null) category.setName(request.getName());
        if (request.getSortOrder() != null) category.setSortOrder(request.getSortOrder());
        if (request.getIcon() != null) category.setIcon(request.getIcon());
        if (request.getStatus() != null) category.setStatus(request.getStatus());
        categoryMapper.updateById(category);
        log.info("分类更新成功: id={}", id);
        return toResponse(category);
    }

    // ==================== delete ====================
    @Override
    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    public void delete(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        // Step 1：检查是否有子分类
        Long childCount = categoryMapper.selectCount(
            new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id)
                .eq(Category::getStatus, 1)
        );
        if (childCount > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        // Step 2：物理删除（外键 ON DELETE RESTRICT 已保护有商品的分类）
        categoryMapper.deleteById(id);
        log.info("分类删除成功: id={}", id);
    }

    // ==================== 辅助方法 ====================
    private CategoryResponse toResponse(Category c) {
        CategoryResponse vo = new CategoryResponse();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setParentId(c.getParentId());
        vo.setLevel(c.getLevel());
        vo.setIcon(c.getIcon());
        vo.setSortOrder(c.getSortOrder());
        vo.setStatus(c.getStatus());
        return vo;
    }

    private CategoryResponse toTreeResponse(Category c) {
        CategoryResponse vo = new CategoryResponse();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setLevel(c.getLevel());
        vo.setIcon(c.getIcon());
        vo.setSortOrder(c.getSortOrder());
        return vo;
    }
}
