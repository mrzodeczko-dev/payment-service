package com.rzodeczko.paymentservice.infrastructure.notification.dto;

import java.util.UUID;

/**
 * Request payload sent to an external notification endpoint to confirm a payment.
 *
 * @param paymentId unique identifier of the confirmed payment
 */
public record PaymentConfirmationRequestDto(UUID paymentId) {
}
