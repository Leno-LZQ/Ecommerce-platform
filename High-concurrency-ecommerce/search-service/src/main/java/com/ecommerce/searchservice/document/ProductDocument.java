package com.ecommerce.searchservice.document;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Document(indexName = "product_index")
public class ProductDocument {
    @Id
    private Long id;                    // product.id

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;                // 商品名

    @Field(type = FieldType.Keyword)
    private String brand;               // 品牌（精确匹配）

    @Field(type = FieldType.Long)
    private Long categoryId;            // 分类ID（聚合用）

    @Field(type = FieldType.Keyword)
    private String categoryName;        // 分类名（展示）

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal price;           // 最低价（排序用）

    @Field(type = FieldType.Integer)
    private Integer salesCount;         // 销量（排序用）

    @Field(type = FieldType.Float)
    private Float rating;               // 评分（排序用）

    @Field(type = FieldType.Keyword)
    private String mainImage;           // 主图

    @Field(type = FieldType.Integer)
    private Integer status;             // 状态（1=上架, 0=下架）
}
