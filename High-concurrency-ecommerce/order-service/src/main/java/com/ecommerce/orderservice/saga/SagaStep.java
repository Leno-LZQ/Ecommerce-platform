package com.ecommerce.orderservice.saga;

public interface SagaStep {

    /** 执行正向操作，返回补偿需要的标识（如 payNo） */
    void execute(SagaContext context);

    /** 补偿操作 */
    void compensate(SagaContext context);
}

