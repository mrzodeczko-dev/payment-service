package com.rzodeczko.paymentservice.application.port.output;

import com.rzodeczko.paymentservice.application.port.input.NotificationCommand;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound application port that defines integration contracts with an external
 * payment gateway.
 *
 * <p>The application layer depends on this abstraction to initialize payments,
 * validate asynchronous gateway notifications, and confirm transaction state.</p>
 */
public interface PaymentGatewayPort {

    /**
     * Registers a new transaction in the external payment gateway.
     *
     * @param orderId internal order identifier bound to the payment attempt
     * @param amount monetary amount expected by the gateway for this transaction
     * @param email customer email provided to the gateway for payer context
     * @param name customer display name provided to the gateway
     * @return gateway registration result containing redirect URL and external transaction ID
     */
    GatewayResult registerTransaction(UUID orderId, BigDecimal amount, String email, String name);

    /**
     * Verifies whether a gateway transaction reached the confirmed state.
     *
     * @param externalTransactionId gateway-side transaction identifier
     * @return {@code true} when the transaction is confirmed, otherwise {@code false}
     */
    boolean verifyTransactionConfirmed(String externalTransactionId);

    /**
     * Validates the authenticity of an incoming gateway notification.
     *
     * @param notification normalized notification payload received by the application
     * @return {@code true} when signature validation succeeds, otherwise {@code false}
     */
    boolean verifyNotificationSignature(NotificationCommand notification);
}
