package com.ecommerce.orderservice.saga;

import com.ecommerce.client.PaymentClient;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.dto.payment.PayCloseRequest;
import com.ecommerce.dto.payment.PayCreateRequest;
import com.ecommerce.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreatePaymentStep implements SagaStep {

    private final PaymentClient paymentClient;

    @Override
    public void execute(SagaContext context) {
        PayCreateRequest request = new PayCreateRequest();
        request.setOrderNo(context.getOrderNo());
        request.setUserId(context.getUserId());
        request.setPayAmount(context.getPayAmount());

        String payNo = paymentClient.create(request).getData();

        if (payNo == null || payNo.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_CREATE_FAILED);
        }

        // 存回 context：编排器把 payNo 传给 compensate 用
        context.setPayNo(payNo);
        log.info("创建支付单成功: orderNo={}, payNo={}", context.getOrderNo(), payNo);
    }

    @Override
    public void compensate(SagaContext context) {
        log.info("补偿关闭支付单: payNo={}", context.getPayNo());
        PayCloseRequest request = new PayCloseRequest();
        request.setPayNo(context.getPayNo());
        paymentClient.close(request);
    }
}
