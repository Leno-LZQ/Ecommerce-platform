package com.ecommerce.searchservice.controller;

import com.ecommerce.result.Result;
import com.ecommerce.searchservice.dto.SearchRequest;
import com.ecommerce.searchservice.dto.SearchResultVO;
import com.ecommerce.searchservice.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品搜索接口。
 *
 * 路由建议：gateway 将 /api/search/** 转发到 search-service。
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 商品全文搜索（分页 + 筛选 + 排序 + 聚合）。
     *
     * 请求示例：
     *   GET /api/search?keyWord=手机&categoryId=1&minPrice=100&maxPrice=10000&sort=default&page=1&size=20
     */
    @GetMapping
    public Result<SearchResultVO> search(@Valid SearchRequest request) {
        return Result.success(searchService.search(request));
    }

    /**
     * 搜索联想 —— 用户输入部分关键词，返回商品名补全列表。
     *
     * 请求示例：
     *   GET /api/search/suggest?keyword=ip&limit=10
     */
    @GetMapping("/suggest")
    public Result<List<String>> suggest(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "10") int limit) {
        return Result.success(searchService.suggest(keyword, limit));
    }

    /**
     * 热门搜索词。
     *
     * 请求示例：
     *   GET /api/search/hot?limit=10
     */
    @GetMapping("/hot")
    public Result<List<String>> hotKeywords(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(searchService.hotKeywords(limit));
    }
}
