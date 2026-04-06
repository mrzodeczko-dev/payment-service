package com.rzodeczko.paymentservice.infrastructure.transaction;


import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.application.port.input.NotificationCommand;
import com.rzodeczko.paymentservice.application.port.input.PaymentUseCase;
import com.rzodeczko.paymentservice.application.service.PaymentService;
import com.rzodeczko.paymentservice.domain.exception.PaymentAlreadyExistsException;
import com.rzodeczko.paymentservice.domain.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Transactional implementation of the {@link PaymentUseCase} interface.
 * <p>
 * This component ensures atomicity of payment operations by wrapping them in database transactions.
 * It guarantees that critical operations either complete successfully or are fully rolled back in case of failure.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Initializing new payments with order and customer information</li>
 *   <li>Handling payment notifications from external payment gateways</li>
 *   <li>Ensuring consistency between payment status and outbox events</li>
 * </ul>
 *
 * <p>Transaction guarantees:
 * <ul>
 *   <li>Payment initialization: findByOrderId + registerTransaction + save operations are atomic</li>
 *   <li>Notification handling: payment confirmation + outbox event persistence are atomic</li>
 * </ul>
 *
 * @see PaymentUseCase
 * @see PaymentService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionalPaymentUseCase implements PaymentUseCase {

    private final PaymentService paymentService;

    /**
     * Initializes a new payment for the given order.
     *
     * <p>This method ensures ACID properties by executing the following operations within a single database transaction:
     * <ul>
     *   <li>Checking if payment for the order already exists</li>
     *   <li>Registering the transaction with the payment service</li>
     *   <li>Persisting the payment to the database</li>
     * </ul>
     * <p>
     * If any operation fails, the entire transaction is rolled back, preventing partial payment records.
     *
     * @param orderId the unique identifier of the order (must not be null)
     * @param amount  the payment amount (must not be null or negative)
     * @param email   the customer's email address (must not be null)
     * @param name    the customer's name (must not be null)
     * @return {@link InitPaymentResult} containing the payment ID and redirect URL for payment processing
     * @throws PaymentAlreadyExistsException if a payment for this order already exists
     * @see PaymentService#initPayment(UUID, BigDecimal, String, String)
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public InitPaymentResult initPayment(UUID orderId, BigDecimal amount, String email, String name) {
        log.info("Init payment. orderId={}, amount={}", orderId, amount);
        InitPaymentResult result = paymentService.initPayment(orderId, amount, email, name);
        log.info("Init payment done. orderId={}, paymentId={}", orderId, result.paymentId());
        return result;
    }

    /**
     * Handles a payment notification from an external payment gateway.
     *
     * <p>This method ensures ACID properties by executing the following operations within a single database transaction:
     * <ul>
     *   <li>Finding the payment by transaction ID</li>
     *   <li>Confirming the payment and updating its status</li>
     *   <li>Creating and persisting an outbox event for eventual consistency</li>
     * </ul>
     *
     * <p>Transaction guarantees prevent data inconsistencies:
     * <ul>
     *   <li>Status can never be PAID without a corresponding outbox event</li>
     *   <li>An outbox event can never exist without the payment being marked as PAID</li>
     * </ul>
     * <p>
     * The outbox event is later processed by an event sender to ensure reliable delivery to subscribers.
     *
     * @param notification the notification command containing transaction ID and status from the payment gateway
     * @throws PaymentNotFoundException if no payment with the given transaction ID exists
     * @see PaymentService#handleNotification(NotificationCommand)
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void handleNotification(NotificationCommand notification) {
        log.info("Handling notification. trId={}, trStatus={}", notification.trId(), notification.trStatus());
        paymentService.handleNotification(notification);
        log.info("Notofication handled. trId={}", notification.trId());
    }
}
