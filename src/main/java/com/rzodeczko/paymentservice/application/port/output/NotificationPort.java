package com.rzodeczko.paymentservice.application.port.output;

import java.util.UUID;

/**
 * Outbound application port responsible for notifying an external system
 * about a successfully initialized payment.
 *
 * <p>Implementations encapsulate integration details (e.g. HTTP client,
 * message broker, webhook) and expose a stable contract to the application layer.</p>
 */
public interface NotificationPort {

    /**
     * Publishes a payment-related notification to an external service.
     *
     * @param orderId unique identifier of the order associated with the payment
     * @param paymentId unique identifier of the payment that should be reported
     */
    void notifyExternalService(UUID orderId, UUID paymentId);
}
