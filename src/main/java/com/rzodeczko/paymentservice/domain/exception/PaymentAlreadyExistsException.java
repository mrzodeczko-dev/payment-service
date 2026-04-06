package com.rzodeczko.paymentservice.domain.exception;

import java.util.UUID;

/**
 * Signals that a payment already exists for the given order identifier.
 *
 * <p>Used to translate persistence-level uniqueness conflicts into a domain-specific
 * error that can be handled as an idempotent business scenario.</p>
 */
public class PaymentAlreadyExistsException extends RuntimeException {
    public PaymentAlreadyExistsException(UUID orderId) {
        super("Payment already exists for order id " + orderId);
    }
}
