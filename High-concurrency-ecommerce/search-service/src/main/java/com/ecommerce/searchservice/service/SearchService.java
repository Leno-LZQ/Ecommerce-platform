package com.ecommerce.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class SearchService {

    private final ElasticsearchClient esClient;

    public SearchService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public SearchResultVO search(SearchRequest request) {

        // ============ 1. 构建 BoolQuery ============
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if (request.getKeyWord() != null && !request.getKeyWord().isBlank()) {
            boolBuilder.must(Query.of(q -> q.multiMatch(mm -> mm
                .query(request.getKeyWord())
                .fields("name^3", "brand")
                .fuzziness("AUTO"))));
        }

        boolBuilder.filter(Query.of(q -> q.term(t -> t
            .field("status").value(1))));

        if (request.getCategoryId() != null) {
            boolBuilder.filter(Query.of(q -> q.term(t -> t
                .field("categoryId").value(request.getCategoryId()))));
        }

        BigDecimal min = request.getMinPrice();
        BigDecimal max = request.getMaxPrice();
        if (min != null || max != null) {
            boolBuilder.filter(Query.of(q -> q.range(r -> r
                .number(n -> {
                    n.field("price");
                    if (min != null) n.gte(min.doubleValue());
                    if (max != null) n.lte(max.doubleValue());
                    return n;
                }))));
        }

        Query query = Query.of(q -> q.bool(boolBuilder.build()));

        // ============ 2. 排序 ============
        String sort = request.getSort() != null ? request.getSort() : "default";
        boolean hasKeyword = request.getKeyWord() != null && !request.getKeyWord().isBlank();

        SortOrder sortOrder;
        switch (sort) {
            case "sales"      -> sortOrder = SortOrder.Desc;
            case "price_asc"  -> sortOrder = SortOrder.Asc;
            case "price_desc" -> sortOrder = SortOrder.Desc;
            case "rating"     -> sortOrder = SortOrder.Desc;
            default -> sortOrder = hasKeyword ? SortOrder.Desc : SortOrder.Desc;  // _score 默认 desc
        }

        // sort 字段名
        String sortField = switch (sort) {
            case "sales"      -> "salesCount";
            case "price_asc"  -> "price";
            case "price_desc" -> "price";
            case "rating"     -> "rating";
            default -> "_score";
        };

        // ============ 3. 执行 ES 搜索 ============
        SearchResponse<ProductDocument> response = null;
        try {
            response = esClient.search(s -> s
                    .index("product_index")
                    .query(query)
                    .from((request.getPage() - 1) * request.getSize())
                    .size(request.getSize())
                    .sort(so -> so.field(f -> f.field(sortField).order(sortOrder)))
                    .aggregations("by_category",
                        Aggregation.of(a -> a.terms(t -> t.field("categoryId").size(50))))
                    .aggregations("by_brand",
                        Aggregation.of(a -> a.terms(t -> t.field("brand").size(50))))
                    .aggregations("by_price",
                        Aggregation.of(a -> a.range(r -> r.field("price")
                            .ranges(
                                AggregationRange.of(ra -> ra.from(0.0).to(500.0)),
                                AggregationRange.of(ra -> ra.from(500.0).to(1000.0)),
                                AggregationRange.of(ra -> ra.from(1000.0).to(2000.0)),
                                AggregationRange.of(ra -> ra.from(2000.0).to(5000.0)),
                                AggregationRange.of(ra -> ra.from(5000.0).to(Double.MAX_VALUE))
                            )
                        )))
                , ProductDocument.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // ============ 4. 提取 Items ============
        List<SearchItem> items = response.hits().hits().stream()
            .map(h -> {
                ProductDocument doc = h.source();
                if (doc == null) return null;
                return new SearchItem(
                    doc.getId(),
                    doc.getName(),
                    doc.getPrice(),
                    doc.getSalesCount(),
                    doc.getRating() != null ? doc.getRating().doubleValue() : null,
                    doc.getMainImage()
                );
            })
            .filter(java.util.Objects::nonNull)
            .toList();

        long total = response.hits().total() != null ? response.hits().total().value() : 0L;

        // ============ 5. 提取聚合 ============
        List<CategoryAggregation> categories = List.of();
        List<BrandAggregation> brands = List.of();
        List<PriceRangeAggregation> priceRanges = List.of();

        Map<String, Aggregate> aggMap = response.aggregations();

        Aggregate catAgg = aggMap.get("by_category");
        if (catAgg != null && catAgg.isSterms()) {
            categories = catAgg.sterms().buckets().array().stream()
                .map(b -> new CategoryAggregation(
                    Long.valueOf(b.key().stringValue()),
                    "",                     // TODO: 缓存补分类名
                    b.docCount()
                ))
                .toList();
        }

        Aggregate brandAgg = aggMap.get("by_brand");
        if (brandAgg != null && brandAgg.isSterms()) {
            brands = brandAgg.sterms().buckets().array().stream()
                .map(b -> new BrandAggregation(b.key().stringValue(), b.docCount()))
                .toList();
        }

        Aggregate priceAgg = aggMap.get("by_price");
        if (priceAgg != null && priceAgg.isRange()) {
            priceRanges = priceAgg.range().buckets().array().stream()
                .filter(b -> b.docCount() > 0)
                .map(b -> new PriceRangeAggregation(b.key(), b.docCount()))
                .toList();
        }

        // ============ 6. 组装返回 ============
        SearchResultVO result = new SearchResultVO();
        result.setTotal(total);
        result.setPage(request.getPage());
        result.setSize(request.getSize());
        result.setItems(items);

        SearchResultVO.Aggregations aggs = new SearchResultVO.Aggregations();
        aggs.setCategories(categories);
        aggs.setBrands(brands);
        aggs.setPriceRanges(priceRanges);
        result.setAggregations(aggs);

        return result;
    }

    /**
     * 搜索联想：输入部分关键词 → 返回商品名补全列表
     */
    public List<String> suggest(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();

        SearchResponse<ProductDocument> response = null;
        try {
            response = esClient.search(s -> s
                    .index("product_index")
                    .query(q -> q.bool(b -> b
                        .filter(f -> f.term(t -> t.field("status").value(1)))
                        .must(m -> m.matchPhrasePrefix(mp -> mp
                            .field("name")
                            .query(keyword)
                        ))
                    ))
                    .size(limit)
                    .sort(so -> so.field(f -> f.field("_score").order(SortOrder.Desc)))
                , ProductDocument.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return response.hits().hits().stream()
            .map(h -> h.source())
            .filter(Objects::nonNull)
            .map(ProductDocument::getName)
            .distinct()
            .toList();
    }

    /**
     * 热门搜索词：基于商品销量/名称统计
     */
    public List<String> hotKeywords(int limit) {
        SearchResponse<ProductDocument> response = null;
        try {
            response = esClient.search(s -> s
                    .index("product_index")
                    .query(q -> q.term(t -> t.field("status").value(1)))
                    .size(Math.max(limit * 2, 1))
                    .sort(so -> so.field(f -> f.field("salesCount").order(SortOrder.Desc)))
                , ProductDocument.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return response.hits().hits().stream()
            .map(h -> h.source())
            .filter(Objects::nonNull)
            .map(ProductDocument::getName)
            .filter(Objects::nonNull)
            .filter(word -> word.length() >= 2)
            .distinct()
            .limit(limit)
            .toList();
    }
}
