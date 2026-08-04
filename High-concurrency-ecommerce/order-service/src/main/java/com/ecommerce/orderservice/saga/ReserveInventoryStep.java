package com.ecommerce.orderservice.saga;

import com.ecommerce.client.InventoryClient;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.dto.inventory.ReserveItem;
import com.ecommerce.dto.inventory.ReserveRequest;
import com.ecommerce.dto.inventory.ReserveResult;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ReserveInventoryStep implements SagaStep {

    @Autowired private final InventoryClient inventoryClient;

    public ReserveInventoryStep(InventoryClient inventoryClient) {  // 构造器注入
        this.inventoryClient = inventoryClient;
    }

    @Override
    public void execute(SagaContext context) {
        String orderNo = context.getOrderNo();
        List<ReserveItem> items = context.getItems().stream()
            .map(item -> new ReserveItem(item.getSkuId(), item.getQuantity()))
            .toList();

        Result<ReserveResult> result = inventoryClient.reserve(new ReserveRequest(orderNo, items));
        if (result == null || result.getData() == null || !result.getData().isSuccess()) {
            throw new BusinessException(ErrorCode.INVENTORY_SHORTAGE);
        }
    }

    @Override
    public void compensate(SagaContext context) {
        log.info("补偿释放库存: orderNo={}", context.getOrderNo());
        inventoryClient.release(Map.of("orderNo", context.getOrderNo()));
    }

}
