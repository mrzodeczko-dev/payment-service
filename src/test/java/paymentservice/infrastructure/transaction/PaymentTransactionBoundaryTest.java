package paymentservice.infrastructure.transaction;

import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.application.service.PaymentService;
import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.infrastructure.transaction.PaymentTransactionBoundary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionBoundaryTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentTransactionBoundary paymentTransactionBoundary;

    @Test
    void findExistingPayment_shouldDelegateToPaymentService() {
        UUID orderId = UUID.randomUUID();
        Payment payment = buildPayment();
        when(paymentService.findExistingPayment(orderId)).thenReturn(Optional.of(payment));

        Optional<Payment> result = paymentTransactionBoundary.findExistingPayment(orderId);

        assertThat(result).containsSame(payment);
        verify(paymentService).findExistingPayment(orderId);
    }

    @Test
    void savePayment_shouldDelegateToPaymentService() {
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String externalTransactionId = "ext-123";
        String redirectUrl = "https://gateway.example.com/pay";
        InitPaymentResult expected = new InitPaymentResult(UUID.randomUUID(), redirectUrl);

        when(paymentService.saveNewPayment(orderId, amount, externalTransactionId, redirectUrl)).thenReturn(expected);

        InitPaymentResult result = paymentTransactionBoundary.savePayment(orderId, amount, externalTransactionId, redirectUrl);

        assertThat(result).isSameAs(expected);
        verify(paymentService).saveNewPayment(orderId, amount, externalTransactionId, redirectUrl);
    }

    @Test
    void getPaymentByExternalId_shouldDelegateToPaymentService() {
        String externalTransactionId = "ext-123";
        Payment payment = buildPayment();
        when(paymentService.getPaymentByExternalId(externalTransactionId)).thenReturn(payment);

        Payment result = paymentTransactionBoundary.getPaymentByExternalId(externalTransactionId);

        assertThat(result).isSameAs(payment);
        verify(paymentService).getPaymentByExternalId(externalTransactionId);
    }

    @Test
    void confirmPayment_shouldDelegateToPaymentService() {
        Payment payment = buildPayment();

        paymentTransactionBoundary.confirmPayment(payment);

        verify(paymentService).confirmPayment(payment);
    }

    @Test
    void failPayment_shouldDelegateToPaymentService() {
        Payment payment = buildPayment();

        paymentTransactionBoundary.failPayment(payment);

        verify(paymentService).failPayment(payment);
    }

    @Test
    void findExistingPayment_shouldBeReadOnlyTransactional() throws NoSuchMethodException {
        Method method = PaymentTransactionBoundary.class.getMethod("findExistingPayment", UUID.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void getPaymentByExternalId_shouldBeReadOnlyTransactional() throws NoSuchMethodException {
        Method method = PaymentTransactionBoundary.class.getMethod("getPaymentByExternalId", String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void savePayment_shouldBeTransactional() throws NoSuchMethodException {
        Method method = PaymentTransactionBoundary.class.getMethod(
                "savePayment",
                UUID.class,
                BigDecimal.class,
                String.class,
                String.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void confirmPayment_shouldBeTransactional() throws NoSuchMethodException {
        Method method = PaymentTransactionBoundary.class.getMethod("confirmPayment", Payment.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void failPayment_shouldBeTransactional() throws NoSuchMethodException {
        Method method = PaymentTransactionBoundary.class.getMethod("failPayment", Payment.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private Payment buildPayment() {
        return new Payment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                com.rzodeczko.paymentservice.domain.model.PaymentStatus.PENDING,
                "ext-" + UUID.randomUUID(),
                "https://gateway.example.com/pay",
                Instant.now()
        );
    }
}

