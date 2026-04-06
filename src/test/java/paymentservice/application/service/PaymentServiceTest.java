package paymentservice.application.service;

import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.application.service.PaymentService;
import com.rzodeczko.paymentservice.domain.exception.PaymentNotFoundException;
import com.rzodeczko.paymentservice.domain.model.OutboxEvent;
import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.model.PaymentStatus;
import com.rzodeczko.paymentservice.domain.repository.OutboxEventRepository;
import com.rzodeczko.paymentservice.domain.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void findExistingPayment_shouldReturnExistingPayment_whenRepositoryFindsOne() {
        UUID orderId = UUID.randomUUID();
        Payment payment = buildPayment(PaymentStatus.PENDING);

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        Optional<Payment> result = paymentService.findExistingPayment(orderId);

        assertThat(result).containsSame(payment);
        verify(paymentRepository).findByOrderId(orderId);
    }

    @Test
    void saveNewPayment_shouldPersistCreatedPaymentAndReturnInitResult() {
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String externalTransactionId = "ext-123";
        String redirectUrl = "https://gateway.com/pay";
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InitPaymentResult result = paymentService.saveNewPayment(orderId, amount, externalTransactionId, redirectUrl);

        assertThat(result.redirectUrl()).isEqualTo(redirectUrl);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void getPaymentByExternalId_shouldReturnPayment_whenFound() {
        Payment payment = buildPayment(PaymentStatus.PENDING);
        when(paymentRepository.findByExternalTransactionId("ext-1")).thenReturn(Optional.of(payment));

        Payment result = paymentService.getPaymentByExternalId("ext-1");

        assertThat(result).isSameAs(payment);
    }

    @Test
    void getPaymentByExternalId_shouldThrowPaymentNotFoundException_whenMissing() {
        when(paymentRepository.findByExternalTransactionId("ext-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByExternalId("ext-missing"))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("ext-missing");
    }

    @Test
    void confirmPayment_shouldMarkAsPaidPersistAndCreateOutboxEvent() {
        Payment payment = buildPayment(PaymentStatus.PENDING);
        when(paymentRepository.save(payment)).thenReturn(payment);

        paymentService.confirmPayment(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).save(payment);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void failPayment_shouldMarkAsFailedAndPersistWithoutOutbox() {
        Payment payment = buildPayment(PaymentStatus.PENDING);
        when(paymentRepository.save(payment)).thenReturn(payment);

        paymentService.failPayment(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(outboxEventRepository, never()).save(any());
    }

    private Payment buildPayment(PaymentStatus status) {
        return new Payment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                status,
                "ext-" + UUID.randomUUID(),
                "https://gateway.example.com/pay",
                Instant.now()
        );
    }
}
