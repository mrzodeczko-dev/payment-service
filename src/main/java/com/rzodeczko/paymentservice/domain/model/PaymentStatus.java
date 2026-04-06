package com.rzodeczko.paymentservice.domain.model;

/**
 * Current lifecycle status of a payment.
 */
public enum PaymentStatus {

    /** Payment has been created and is waiting for provider confirmation. */
    PENDING,

    /** Payment has been successfully confirmed. */
    PAID,

    /** Payment was rejected or could not be completed. */
    FAILED
}
