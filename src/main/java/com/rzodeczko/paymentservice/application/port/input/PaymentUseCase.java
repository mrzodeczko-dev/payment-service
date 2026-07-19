package com.rzodeczko.paymentservice.application.port.input;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input port exposing payment-related application use cases.
 *
 * <p>This interface defines business capabilities from the perspective of external clients
 * (for example, a REST controller), without leaking infrastructure concerns.
 */
public interface PaymentUseCase {

    /**
     * Initializes a payment for a given order.
     *
     * @param orderId unique identifier of the order
     * @param amount payment amount requested for the order
     * @param email customer email used by the payment provider
     * @param name customer name used by the payment provider
     * @return result containing the created payment identifier and redirect URL
     */
    InitPaymentResult initPayment(UUID orderId, BigDecimal amount, String email, String name);

    /**
     * Processes an asynchronous notification (webhook) sent by the payment provider.
     *
     * @param notification notification payload mapped to application command object
     */
    void handleNotification(NotificationCommand notification);

    /**
     * Refunds a payment by its identifier.
     *
     * @param paymentId unique identifier of the payment to refund
     */
    void refundPayment(UUID paymentId);
}
