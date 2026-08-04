package com.ecommerce.productservice.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.productservice.entity.Product;
import com.ecommerce.productservice.mapper.ProductMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class BloomFilterService {

    @Autowired
    private RBloomFilter<Long> bloomFilter;  // Redisson RBloomFilter Bean
    @Autowired private ProductMapper productMapper;

    @PostConstruct
    public void init() {
        // 从 MySQL 加载全量商品 ID 初始化 BloomFilter
        List<Product> all = productMapper.selectList(
            new LambdaQueryWrapper<Product>()
                .eq(Product::getDeleted, 0)
                .select(Product::getId)
        );
        for (Product p : all) {
            bloomFilter.add(p.getId());
        }
        log.info("BloomFilter 初始化完成: 加载 {} 个商品ID, 预期容量={}, 误判率={}",
            all.size(),
            bloomFilter.getExpectedInsertions(),
            bloomFilter.getFalseProbability());
    }

    /** 判断商品ID是否可能存在 */
    public boolean mightContain(Long productId) {
        return bloomFilter.contains(productId);
    }

    /** 新增商品时添加到 BloomFilter */
    public void put(Long productId) {
        bloomFilter.add(productId);
    }

    /** 定时任务：每日凌晨 3 点重建 BloomFilter */
    @Scheduled(cron = "0 0 3 * * ?")
    public void rebuild() {
        log.info("开始重建 BloomFilter...");
        bloomFilter.delete();  // 删除旧过滤器
        init();                // 重新加载
        log.info("BloomFilter 重建完成");
    }
}
