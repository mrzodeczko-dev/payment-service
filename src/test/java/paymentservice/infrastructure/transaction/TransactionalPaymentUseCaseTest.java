package paymentservice.infrastructure.transaction;

import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.application.port.input.NotificationCommand;
import com.rzodeczko.paymentservice.application.port.output.GatewayResult;
import com.rzodeczko.paymentservice.application.port.output.PaymentGatewayPort;
import com.rzodeczko.paymentservice.domain.exception.InvalidNotificationSignatureException;
import com.rzodeczko.paymentservice.domain.exception.PaymentAlreadyExistsException;
import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.model.PaymentStatus;
import com.rzodeczko.paymentservice.infrastructure.transaction.PaymentTransactionBoundary;
import com.rzodeczko.paymentservice.infrastructure.transaction.PaymentUseCaseImpl;
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
class TransactionalPaymentUseCaseTest {

    @Mock
    private PaymentTransactionBoundary paymentTransactionBoundary;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @InjectMocks
    private PaymentUseCaseImpl paymentUseCase;

    @Test
    void initPayment_shouldReturnExistingPayment_whenPaymentAlreadyExists() {
        UUID orderId = UUID.randomUUID();
        Payment existing = buildPayment(orderId, PaymentStatus.PENDING, "ext-1", "https://gateway.com/pay");

        when(paymentTransactionBoundary.findExistingPayment(orderId)).thenReturn(Optional.of(existing));

        InitPaymentResult result = paymentUseCase.initPayment(orderId, new BigDecimal("100.00"), "user@example.com", "John Doe");

        assertThat(result.paymentId()).isEqualTo(existing.getId());
        assertThat(result.redirectUrl()).isEqualTo(existing.getRedirectUrl());
        verify(paymentGatewayPort, never()).registerTransaction(any(), any(), any(), any());
    }

    @Test
    void initPayment_shouldRegisterGatewayAndSavePayment_whenPaymentDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("150.00");
        GatewayResult gatewayResult = new GatewayResult("https://gateway.com/pay", "ext-123");
        InitPaymentResult expected = new InitPaymentResult(UUID.randomUUID(), gatewayResult.redirectUrl());

        when(paymentTransactionBoundary.findExistingPayment(orderId)).thenReturn(Optional.empty());
        when(paymentGatewayPort.registerTransaction(orderId, amount, "user@example.com", "John Doe")).thenReturn(gatewayResult);
        when(paymentTransactionBoundary.savePayment(orderId, amount, gatewayResult.externalTransactionId(), gatewayResult.redirectUrl()))
                .thenReturn(expected);

        InitPaymentResult result = paymentUseCase.initPayment(orderId, amount, "user@example.com", "John Doe");

        assertThat(result).isEqualTo(expected);
        verify(paymentGatewayPort).registerTransaction(orderId, amount, "user@example.com", "John Doe");
        verify(paymentTransactionBoundary).savePayment(orderId, amount, gatewayResult.externalTransactionId(), gatewayResult.redirectUrl());
    }

    @Test
    void initPayment_shouldResolveConflictByReadingExistingPayment_whenSaveThrowsPaymentAlreadyExistsException() {
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("90.00");
        GatewayResult gatewayResult = new GatewayResult("https://gateway.com/pay", "ext-123");
        Payment existing = buildPayment(orderId, PaymentStatus.PENDING, "ext-1", "https://gateway.com/pay");

        when(paymentTransactionBoundary.findExistingPayment(orderId)).thenReturn(Optional.empty(), Optional.of(existing));
        when(paymentGatewayPort.registerTransaction(orderId, amount, "user@example.com", "John Doe")).thenReturn(gatewayResult);
        when(paymentTransactionBoundary.savePayment(orderId, amount, gatewayResult.externalTransactionId(), gatewayResult.redirectUrl()))
                .thenThrow(new PaymentAlreadyExistsException(orderId));

        InitPaymentResult result = paymentUseCase.initPayment(orderId, amount, "user@example.com", "John Doe");

        assertThat(result.paymentId()).isEqualTo(existing.getId());
        assertThat(result.redirectUrl()).isEqualTo(existing.getRedirectUrl());
    }

    @Test
    void initPayment_shouldPropagatePaymentAlreadyExistsException_whenReadBackFailsAfterConflict() {
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("90.00");
        GatewayResult gatewayResult = new GatewayResult("https://gateway.com/pay", "ext-123");

        when(paymentTransactionBoundary.findExistingPayment(orderId)).thenReturn(Optional.empty(), Optional.empty());
        when(paymentGatewayPort.registerTransaction(orderId, amount, "user@example.com", "John Doe")).thenReturn(gatewayResult);
        when(paymentTransactionBoundary.savePayment(orderId, amount, gatewayResult.externalTransactionId(), gatewayResult.redirectUrl()))
                .thenThrow(new PaymentAlreadyExistsException(orderId));

        assertThatThrownBy(() -> paymentUseCase.initPayment(orderId, amount, "user@example.com", "John Doe"))
                .isInstanceOf(PaymentAlreadyExistsException.class);
    }

    @Test
    void handleNotification_shouldThrowInvalidNotificationSignatureException_whenSignatureInvalid() {
        NotificationCommand notification = notification("tr-1", "TRUE");
        when(paymentGatewayPort.verifyNotificationSignature(notification)).thenReturn(false);

        assertThatThrownBy(() -> paymentUseCase.handleNotification(notification))
                .isInstanceOf(InvalidNotificationSignatureException.class);

        verify(paymentTransactionBoundary, never()).getPaymentByExternalId(any());
    }

    @Test
    void handleNotification_shouldReturnImmediately_whenPaymentAlreadyPaid() {
        NotificationCommand notification = notification("tr-1", "TRUE");
        Payment paid = buildPayment(UUID.randomUUID(), PaymentStatus.PAID, "tr-1", "https://gateway.com/pay");
        when(paymentGatewayPort.verifyNotificationSignature(notification)).thenReturn(true);
        when(paymentTransactionBoundary.getPaymentByExternalId("tr-1")).thenReturn(paid);

        paymentUseCase.handleNotification(notification);

        verify(paymentGatewayPort, never()).verifyTransactionConfirmed(any());
        verify(paymentTransactionBoundary, never()).confirmPayment(any());
        verify(paymentTransactionBoundary, never()).failPayment(any());
    }

    @Test
    void handleNotification_shouldConfirmPayment_whenStatusTrueAndConfirmed() {
        NotificationCommand notification = notification("tr-1", "TRUE");
        Payment pending = buildPayment(UUID.randomUUID(), PaymentStatus.PENDING, "tr-1", "https://gateway.com/pay");
        when(paymentGatewayPort.verifyNotificationSignature(notification)).thenReturn(true);
        when(paymentTransactionBoundary.getPaymentByExternalId("tr-1")).thenReturn(pending);
        when(paymentGatewayPort.verifyTransactionConfirmed("tr-1")).thenReturn(true);

        paymentUseCase.handleNotification(notification);

        verify(paymentTransactionBoundary).confirmPayment(pending);
        verify(paymentTransactionBoundary, never()).failPayment(any());
    }

    @Test
    void handleNotification_shouldDoNothing_whenStatusTrueButNotConfirmed() {
        NotificationCommand notification = notification("tr-1", "TRUE");
        Payment pending = buildPayment(UUID.randomUUID(), PaymentStatus.PENDING, "tr-1", "https://gateway.com/pay");
        when(paymentGatewayPort.verifyNotificationSignature(notification)).thenReturn(true);
        when(paymentTransactionBoundary.getPaymentByExternalId("tr-1")).thenReturn(pending);
        when(paymentGatewayPort.verifyTransactionConfirmed("tr-1")).thenReturn(false);

        paymentUseCase.handleNotification(notification);

        verify(paymentTransactionBoundary, never()).confirmPayment(any());
        verify(paymentTransactionBoundary, never()).failPayment(any());
    }

    @Test
    void handleNotification_shouldFailPayment_whenStatusIsNotTrue() {
        NotificationCommand notification = notification("tr-1", "FALSE");
        Payment pending = buildPayment(UUID.randomUUID(), PaymentStatus.PENDING, "tr-1", "https://gateway.com/pay");
        when(paymentGatewayPort.verifyNotificationSignature(notification)).thenReturn(true);
        when(paymentTransactionBoundary.getPaymentByExternalId("tr-1")).thenReturn(pending);

        paymentUseCase.handleNotification(notification);

        verify(paymentTransactionBoundary).failPayment(pending);
        verify(paymentGatewayPort, never()).verifyTransactionConfirmed(any());
    }

    private NotificationCommand notification(String trId, String trStatus) {
        return new NotificationCommand(
                "merchant", trId, "date", "crc",
                "100", "100", "desc", trStatus, null, "email", "md5"
        );
    }

    private Payment buildPayment(UUID orderId, PaymentStatus status, String externalTransactionId, String redirectUrl) {
        return new Payment(
                UUID.randomUUID(),
                orderId,
                new BigDecimal("100.00"),
                status,
                externalTransactionId,
                redirectUrl,
                Instant.now()
        );
    }
}
