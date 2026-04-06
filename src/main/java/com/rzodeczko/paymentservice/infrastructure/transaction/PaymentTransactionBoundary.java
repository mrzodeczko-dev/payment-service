package com.rzodeczko.paymentservice.infrastructure.transaction;


import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.application.service.PaymentService;
import com.rzodeczko.paymentservice.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;


/**
 * Defines transactional boundaries around {@link PaymentService} operations.
 *
 * <p>This separate Spring bean exists so {@code @Transactional} advice is applied through
 * proxy calls while orchestration remains outside long-running database transactions.</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentTransactionBoundary {
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public Optional<Payment> findExistingPayment(UUID orderId) {
        return paymentService.findExistingPayment(orderId);
    }

    @Transactional
    public InitPaymentResult savePayment(
            UUID orderId,
            BigDecimal amount,
            String externalTransactionId,
            String redirectUrl
    ) {
        return paymentService.saveNewPayment(orderId, amount, externalTransactionId, redirectUrl);
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByExternalId(String externalTransactionId) {
        return paymentService.getPaymentByExternalId(externalTransactionId);
    }

    @Transactional
    public void confirmPayment(Payment payment) {
        paymentService.confirmPayment(payment);
    }

    @Transactional
    public void failPayment(Payment payment) {
        paymentService.failPayment(payment);
    }
}