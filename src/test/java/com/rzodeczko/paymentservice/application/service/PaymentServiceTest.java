package com.rzodeczko.paymentservice.application.service;

import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.domain.exception.PaymentNotFoundException;
import com.rzodeczko.paymentservice.domain.model.OutboxEvent;
import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.model.PaymentStatus;
import com.rzodeczko.paymentservice.domain.repository.OutboxEventRepository;
import com.rzodeczko.paymentservice.domain.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final PaymentService paymentService = new PaymentService(paymentRepository, outboxEventRepository);

    // ─── findExistingPayment ───────────────────────────────────────────────────

    @Test
    void findExistingPayment_shouldReturnPayment_whenExists() {
        UUID orderId = UUID.randomUUID();
        Payment payment = pendingPayment(orderId);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        Optional<Payment> result = paymentService.findExistingPayment(orderId);

        assertThat(result).isPresent().contains(payment);
    }

    @Test
    void findExistingPayment_shouldReturnEmpty_whenNotExists() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        Optional<Payment> result = paymentService.findExistingPayment(orderId);

        assertThat(result).isEmpty();
    }

    // ─── saveNewPayment ────────────────────────────────────────────────────────

    @Test
    void saveNewPayment_shouldCreateAndSavePayment() {
        UUID orderId = UUID.randomUUID();

        InitPaymentResult result = paymentService.saveNewPayment(
                orderId, new BigDecimal("99.99"), "ext-tx-1", "https://pay.com");

        assertThat(result.paymentId()).isNotNull();
        assertThat(result.redirectUrl()).isEqualTo("https://pay.com");
        verify(paymentRepository).save(any(Payment.class));
    }

    // ─── getPaymentByExternalId ────────────────────────────────────────────────

    @Test
    void getPaymentByExternalId_shouldReturnPayment_whenFound() {
        Payment payment = pendingPayment(UUID.randomUUID());
        when(paymentRepository.findByExternalTransactionId("ext-1")).thenReturn(Optional.of(payment));

        Payment result = paymentService.getPaymentByExternalId("ext-1");

        assertThat(result).isEqualTo(payment);
    }

    @Test
    void getPaymentByExternalId_shouldThrow_whenNotFound() {
        when(paymentRepository.findByExternalTransactionId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByExternalId("missing"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ─── confirmPayment ────────────────────────────────────────────────────────

    @Test
    void confirmPayment_shouldConfirmAndSaveWithOutboxEvent() {
        Payment payment = pendingPayment(UUID.randomUUID());

        paymentService.confirmPayment(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).save(payment);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    // ─── failPayment ───────────────────────────────────────────────────────────

    @Test
    void failPayment_shouldFailAndSave() {
        Payment payment = pendingPayment(UUID.randomUUID());

        paymentService.failPayment(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(outboxEventRepository, never()).save(any());
    }

    // ─── getPaymentById ────────────────────────────────────────────────────────

    @Test
    void getPaymentById_shouldReturnPayment_whenFound() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(paymentId, UUID.randomUUID(), new BigDecimal("50"),
                PaymentStatus.PENDING, "ext-1", "https://r.com", Instant.now());
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        Payment result = paymentService.getPaymentById(paymentId);

        assertThat(result.getId()).isEqualTo(paymentId);
    }

    @Test
    void getPaymentById_shouldThrow_whenNotFound() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(paymentId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ─── refundPayment ─────────────────────────────────────────────────────────

    @Test
    void refundPayment_shouldRefundAndSave() {
        Payment payment = pendingPayment(UUID.randomUUID());
        payment.confirm();

        paymentService.refundPayment(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(payment);
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private Payment pendingPayment(UUID orderId) {
        return new Payment(UUID.randomUUID(), orderId, new BigDecimal("100"),
                PaymentStatus.PENDING, "ext-1", "https://redirect.com", Instant.now());
    }
}
