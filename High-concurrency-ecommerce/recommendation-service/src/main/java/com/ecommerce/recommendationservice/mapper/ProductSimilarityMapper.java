package com.ecommerce.recommendationservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.recommendationservice.entity.ProductSimilarity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductSimilarityMapper extends BaseMapper<ProductSimilarity> {

    /**
     * 查询与指定商品最相似的商品列表。
     */
    @Select("SELECT * FROM product_similarity WHERE product_id_a = #{productId} " +
            "ORDER BY similarity DESC LIMIT #{limit}")
    List<ProductSimilarity> selectTopByProductA(@Param("productId") Long productId,
                                                 @Param("limit") int limit);

}
