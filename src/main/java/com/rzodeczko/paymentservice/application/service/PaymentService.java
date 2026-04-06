package com.rzodeczko.paymentservice.application.service;


import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.domain.exception.PaymentNotFoundException;
import com.rzodeczko.paymentservice.domain.model.OutboxEvent;
import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.repository.OutboxEventRepository;
import com.rzodeczko.paymentservice.domain.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;


/**
 * Application service containing core payment operations used inside transactional boundaries.
 *
 * <p>This service coordinates repository access and domain state transitions, but leaves
 * transaction demarcation and external gateway orchestration to higher layers.</p>
 */
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;

    public PaymentService(PaymentRepository paymentRepository, OutboxEventRepository outboxEventRepository) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    public Optional<Payment> findExistingPayment(UUID orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    public InitPaymentResult saveNewPayment(
            UUID orderId,
            BigDecimal amount,
            String externalTransactionId,
            String redirectUrl) {
        Payment payment = Payment.create(orderId, amount, externalTransactionId, redirectUrl);
        paymentRepository.save(payment);
        return new InitPaymentResult(payment.getId(), payment.getRedirectUrl());
    }


    public Payment getPaymentByExternalId(String externatTransactionId) {
        return paymentRepository
                .findByExternalTransactionId(externatTransactionId)
                .orElseThrow(() -> new PaymentNotFoundException(externatTransactionId));
    }

    public void confirmPayment(Payment payment) {
        payment.confirm();
        paymentRepository.save(payment);
        outboxEventRepository.save(OutboxEvent.create(payment.getOrderId(), payment.getId()));
    }

    public void failPayment(Payment payment) {
        payment.fail();
        paymentRepository.save(payment);
    }
}