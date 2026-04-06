package com.rzodeczko.paymentservice.domain.exception;

import java.util.UUID;

/**
 * Signals that a payment could not be found using the provided business or gateway identifier.
 *
 * <p>This exception is used when application flow expects an existing payment record but the
 * repository lookup returns no result.</p>
 */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String externalTransactionId) {
        super("Payment not found for external transaction id " + externalTransactionId);
    }

    public PaymentNotFoundException(UUID orderId) {
        super("Payment not found for order id " + orderId);
    }
}
