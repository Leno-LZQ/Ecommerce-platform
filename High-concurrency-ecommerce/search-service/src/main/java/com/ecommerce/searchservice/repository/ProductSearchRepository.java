package com.ecommerce.searchservice.repository;

import com.ecommerce.searchservice.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, Long> {

    /**
     * 按状态查询（上架/下架）
     */
    List<ProductDocument> findByStatus(Integer status);

    /**
     * 按分类ID查询
     */
    List<ProductDocument> findByCategoryId(Long categoryId);

}
