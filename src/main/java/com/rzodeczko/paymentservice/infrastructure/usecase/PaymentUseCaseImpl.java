package com.rzodeczko.paymentservice.infrastructure.usecase;

import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.application.port.input.NotificationCommand;
import com.rzodeczko.paymentservice.application.port.input.PaymentUseCase;
import com.rzodeczko.paymentservice.application.port.output.GatewayResult;
import com.rzodeczko.paymentservice.application.port.output.PaymentGatewayPort;
import com.rzodeczko.paymentservice.domain.exception.InvalidNotificationSignatureException;
import com.rzodeczko.paymentservice.domain.exception.PaymentAlreadyExistsException;
import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.infrastructure.transaction.PaymentTransactionBoundary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Orchestrates payment use-cases by combining short database transactions with
 * gateway calls performed outside of transactional scope.
 *
 * <p>This component keeps transaction boundaries in {@link PaymentTransactionBoundary}
 * and delegates provider communication to {@link PaymentGatewayPort}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentUseCaseImpl implements PaymentUseCase {

    private final PaymentTransactionBoundary paymentTransactionBoundary;
    private final PaymentGatewayPort paymentGatewayPort;

    /**
     * Initializes a payment in an idempotent way for a given order.
     *
     * <p>Flow:
     * <ol>
     *   <li>Read existing payment in a short read-only transaction.</li>
     *   <li>If missing, register a transaction in the external gateway (outside DB transaction).</li>
     *   <li>Persist payment in a short write transaction.</li>
     * </ol>
     *
     * <p>If a concurrent request wins the insert race, persistence may throw
     * {@link PaymentAlreadyExistsException}. This method treats it as an idempotent
     * conflict and returns the already persisted payment when it can be read.</p>
     *
     * <p>The read-back after conflict is expected to succeed. If the existing payment
     * still cannot be read for any reason, the original conflict exception is propagated
     * instead of being hidden.</p>
     *
     * @param orderId business identifier of the order
     * @param amount  payment amount
     * @param email   payer email used by the gateway
     * @param name    payer name used by the gateway
     * @return internal payment id and redirect URL for the payer
     * @throws PaymentAlreadyExistsException when insert conflict happens and existing
     *                                       payment cannot be read back
     */
    @Override
    public InitPaymentResult initPayment(UUID orderId, BigDecimal amount, String email, String name) {
        log.info("Init payment. orderId={}, amount={}", orderId, amount);

        return paymentTransactionBoundary
                .findExistingPayment(orderId)
                .map(existing -> {
                    log.info("Payment already exists. orderId={}", orderId);
                    return new InitPaymentResult(existing.getId(), existing.getRedirectUrl());
                })
                .orElseGet(() -> {
                    GatewayResult gatewayResult = paymentGatewayPort.registerTransaction(
                            orderId,
                            amount,
                            email,
                            name
                    );
                    try {
                        InitPaymentResult result = paymentTransactionBoundary.savePayment(
                                orderId,
                                amount,
                                gatewayResult.externalTransactionId(),
                                gatewayResult.redirectUrl()
                        );
                        log.info("Init payment done. orderId={}, paymentId={}", orderId, result.paymentId());
                        return result;
                    } catch (PaymentAlreadyExistsException ex) {
                        log.info("Payment conflict resolved idempotently. orderId={}", orderId);
                        return paymentTransactionBoundary.findExistingPayment(orderId)
                                .map(existing -> new InitPaymentResult(existing.getId(), existing.getRedirectUrl()))
                                .orElseThrow(() -> ex);
                    }
                });
    }

    /**
     * Handles asynchronous gateway notification for an existing transaction.
     *
     * <p>Flow:
     * <ol>
     *   <li>Verify notification signature.</li>
     *   <li>Load payment by external transaction id.</li>
     *   <li>Return early if payment is already paid (idempotency).</li>
     *   <li>For successful status, verify confirmation with gateway and confirm payment.</li>
     *   <li>For non-success status, mark payment as failed.</li>
     * </ol>
     *
     * @param notification gateway callback payload
     * @throws InvalidNotificationSignatureException when signature validation fails
     */
    @Override
    public void handleNotification(NotificationCommand notification) {
        log.info("Handling notification: trId={}, trStatus={}", notification.trId(), notification.trStatus());

        if (!paymentGatewayPort.verifyNotificationSignature(notification)) {
            throw new InvalidNotificationSignatureException();
        }

        Payment payment = paymentTransactionBoundary.getPaymentByExternalId(notification.trCrc());

        if (payment.isPaid()) {
            return;
        }

        if ("TRUE".equalsIgnoreCase(notification.trStatus())) {
            boolean confirmed = paymentGatewayPort.verifyTransactionConfirmed(notification.trId());
            if (confirmed) {
                paymentTransactionBoundary.confirmPayment(payment);
            }
        } else {
            paymentTransactionBoundary.failPayment(payment);
        }

        log.info("Notification handled. trId={}", notification.trId());
    }
}