package com.rzodeczko.paymentservice.infrastructure.usecase;

import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.application.port.input.NotificationCommand;
import com.rzodeczko.paymentservice.application.port.output.GatewayResult;
import com.rzodeczko.paymentservice.application.port.output.PaymentGatewayPort;
import com.rzodeczko.paymentservice.domain.exception.InvalidNotificationSignatureException;
import com.rzodeczko.paymentservice.domain.exception.PaymentAlreadyExistsException;
import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.model.PaymentStatus;
import com.rzodeczko.paymentservice.infrastructure.transaction.PaymentTransactionBoundary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentUseCaseImplTest {

    private final PaymentTransactionBoundary transactionBoundary = mock(PaymentTransactionBoundary.class);
    private final PaymentGatewayPort gatewayPort = mock(PaymentGatewayPort.class);
    private final PaymentUseCaseImpl useCase = new PaymentUseCaseImpl(transactionBoundary, gatewayPort);

    // ─── initPayment ───────────────────────────────────────────────────────────

    @Test
    void initPayment_shouldReturnExistingPayment_whenAlreadyExists() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment existing = new Payment(paymentId, orderId, new BigDecimal("100"),
                PaymentStatus.PENDING, "ext-1", "https://redirect.com", Instant.now());

        when(transactionBoundary.findExistingPayment(orderId)).thenReturn(Optional.of(existing));

        // when
        InitPaymentResult result = useCase.initPayment(orderId, new BigDecimal("100"), "a@b.com", "John");

        // then
        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.redirectUrl()).isEqualTo("https://redirect.com");
        verify(gatewayPort, never()).registerTransaction(any(), any(), any(), any());
    }

    @Test
    void initPayment_shouldRegisterAndSave_whenNoExistingPayment() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        when(transactionBoundary.findExistingPayment(orderId)).thenReturn(Optional.empty());
        when(gatewayPort.registerTransaction(eq(orderId), any(), any(), any()))
                .thenReturn(new GatewayResult("https://pay.com", "ext-tx-1"));
        when(transactionBoundary.savePayment(eq(orderId), any(), eq("ext-tx-1"), eq("https://pay.com")))
                .thenReturn(new InitPaymentResult(paymentId, "https://pay.com"));

        // when
        InitPaymentResult result = useCase.initPayment(orderId, new BigDecimal("50"), "a@b.com", "Jane");

        // then
        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.redirectUrl()).isEqualTo("https://pay.com");
    }

    @Test
    void initPayment_shouldResolveConflict_whenPaymentAlreadyExistsException() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment existing = new Payment(paymentId, orderId, new BigDecimal("100"),
                PaymentStatus.PENDING, "ext-1", "https://redirect.com", Instant.now());

        when(transactionBoundary.findExistingPayment(orderId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(gatewayPort.registerTransaction(any(), any(), any(), any()))
                .thenReturn(new GatewayResult("https://pay.com", "ext-tx-1"));
        when(transactionBoundary.savePayment(any(), any(), any(), any()))
                .thenThrow(new PaymentAlreadyExistsException(orderId));

        // when
        InitPaymentResult result = useCase.initPayment(orderId, new BigDecimal("100"), "a@b.com", "John");

        // then
        assertThat(result.paymentId()).isEqualTo(paymentId);
    }

    @Test
    void initPayment_shouldThrow_whenConflictAndCannotReadBack() {
        // given
        UUID orderId = UUID.randomUUID();

        when(transactionBoundary.findExistingPayment(orderId)).thenReturn(Optional.empty());
        when(gatewayPort.registerTransaction(any(), any(), any(), any()))
                .thenReturn(new GatewayResult("https://pay.com", "ext-tx-1"));
        when(transactionBoundary.savePayment(any(), any(), any(), any()))
                .thenThrow(new PaymentAlreadyExistsException(orderId));

        // when/then
        assertThatThrownBy(() -> useCase.initPayment(orderId, new BigDecimal("100"), "a@b.com", "John"))
                .isInstanceOf(PaymentAlreadyExistsException.class);
    }

    // ─── handleNotification ────────────────────────────────────────────────────

    @Test
    void handleNotification_shouldThrow_whenSignatureInvalid() {
        // given
        NotificationCommand cmd = notificationCommand("TRUE");
        when(gatewayPort.verifyNotificationSignature(cmd)).thenReturn(false);

        // when/then
        assertThatThrownBy(() -> useCase.handleNotification(cmd))
                .isInstanceOf(InvalidNotificationSignatureException.class);
    }

    @Test
    void handleNotification_shouldReturnEarly_whenAlreadyPaid() {
        // given
        NotificationCommand cmd = notificationCommand("TRUE");
        Payment paid = paidPayment();

        when(gatewayPort.verifyNotificationSignature(cmd)).thenReturn(true);
        when(transactionBoundary.getPaymentByExternalId(cmd.trCrc())).thenReturn(paid);

        // when
        useCase.handleNotification(cmd);

        // then
        verify(gatewayPort, never()).verifyTransactionConfirmed(any());
        verify(transactionBoundary, never()).confirmPayment(any());
    }

    @Test
    void handleNotification_shouldConfirm_whenStatusTrueAndVerified() {
        // given
        NotificationCommand cmd = notificationCommand("TRUE");
        Payment pending = pendingPayment();

        when(gatewayPort.verifyNotificationSignature(cmd)).thenReturn(true);
        when(transactionBoundary.getPaymentByExternalId(cmd.trCrc())).thenReturn(pending);
        when(gatewayPort.verifyTransactionConfirmed(cmd.trId())).thenReturn(true);

        // when
        useCase.handleNotification(cmd);

        // then
        verify(transactionBoundary).confirmPayment(pending);
    }

    @Test
    void handleNotification_shouldNotConfirm_whenStatusTrueButNotVerified() {
        // given
        NotificationCommand cmd = notificationCommand("TRUE");
        Payment pending = pendingPayment();

        when(gatewayPort.verifyNotificationSignature(cmd)).thenReturn(true);
        when(transactionBoundary.getPaymentByExternalId(cmd.trCrc())).thenReturn(pending);
        when(gatewayPort.verifyTransactionConfirmed(cmd.trId())).thenReturn(false);

        // when
        useCase.handleNotification(cmd);

        // then
        verify(transactionBoundary, never()).confirmPayment(any());
        verify(transactionBoundary, never()).failPayment(any());
    }

    @Test
    void handleNotification_shouldFail_whenStatusNotTrue() {
        // given
        NotificationCommand cmd = notificationCommand("FALSE");
        Payment pending = pendingPayment();

        when(gatewayPort.verifyNotificationSignature(cmd)).thenReturn(true);
        when(transactionBoundary.getPaymentByExternalId(cmd.trCrc())).thenReturn(pending);

        // when
        useCase.handleNotification(cmd);

        // then
        verify(transactionBoundary).failPayment(pending);
        verify(gatewayPort, never()).verifyTransactionConfirmed(any());
    }

    // ─── refundPayment ─────────────────────────────────────────────────────────

    @Test
    void refundPayment_shouldDelegateToTransactionBoundary() {
        // given
        UUID paymentId = UUID.randomUUID();
        Payment payment = paidPayment();
        when(transactionBoundary.getPaymentById(paymentId)).thenReturn(payment);

        // when
        useCase.refundPayment(paymentId);

        // then
        verify(transactionBoundary).refundPayment(payment);
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private NotificationCommand notificationCommand(String trStatus) {
        return new NotificationCommand(
                "merchant-1", "tr-123", "2026-01-01", "crc-123",
                "100.00", "100.00", "desc", trStatus, "none", "a@b.com", "md5"
        );
    }

    private Payment pendingPayment() {
        return new Payment(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"),
                PaymentStatus.PENDING, "ext-1", "https://redirect.com", Instant.now());
    }

    private Payment paidPayment() {
        Payment p = pendingPayment();
        p.confirm();
        return p;
    }
}
