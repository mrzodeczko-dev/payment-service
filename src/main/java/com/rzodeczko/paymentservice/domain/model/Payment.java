package com.rzodeczko.paymentservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing a single payment in the system.
 *
 * <p>A payment starts in {@link PaymentStatus#PENDING} status and can transition to
 * {@link PaymentStatus#PAID} or {@link PaymentStatus#FAILED}.
 */
public class Payment {
    private final UUID id;
    private final UUID orderId;
    private final BigDecimal amount;

    /** External provider transaction ID (stored as unique in persistence layer). */
    private final String externalTransactionId;
    private final String redirectUrl;
    private final Instant createdAt;
    private PaymentStatus status;

    /**
     * Creates a fully initialized payment entity.
     *
     * @param id payment identifier
     * @param orderId related order identifier
     * @param amount payment amount
     * @param status current payment status
     * @param externalTransactionId external transaction identifier from payment provider
     * @param redirectUrl provider redirect URL for customer checkout
     * @param createdAt creation timestamp
     */
    public Payment(
            UUID id,
            UUID orderId,
            BigDecimal amount,
            PaymentStatus status,
            String externalTransactionId,
            String redirectUrl,
            Instant createdAt
    ) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.externalTransactionId = externalTransactionId;
        this.status = status;
        this.redirectUrl = redirectUrl;
        this.createdAt = createdAt;
    }

    /**
     * Factory method creating a new payment in {@code PENDING} status.
     *
     * @param orderId related order identifier
     * @param amount payment amount (must be positive)
     * @param externalTransactionId external transaction identifier from payment provider
     * @param redirectUrl provider redirect URL for customer checkout
     * @return newly created payment
     * @throws IllegalArgumentException when {@code orderId} is null or {@code amount} is not positive
     */
    public static Payment create(
            UUID orderId,
            BigDecimal amount,
            String externalTransactionId,
            String redirectUrl
    ) {
        if (orderId == null) {
            throw new IllegalArgumentException("Oder id cannot be null");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        return new Payment(
                UUID.randomUUID(),
                orderId,
                amount,
                PaymentStatus.PENDING,
                externalTransactionId,
                redirectUrl,
                Instant.now()
        );
    }

    /**
     * Marks payment as paid.
     *
     * @throws IllegalArgumentException when payment is not in {@code PENDING} status
     */
    public void confirm() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Cannot confirm payment in status: %s. Expected: Pending".formatted(this.status));
        }
        this.status = PaymentStatus.PAID;
    }

    /**
     * Marks payment as failed.
     *
     * @throws IllegalArgumentException when payment is not in {@code PENDING} status
     */
    public void fail() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Cannot fail payment in status: %s. Expected: Pending".formatted(this.status));
        }
        this.status = PaymentStatus.FAILED;
    }

    /** @return payment identifier */
    public UUID getId() {
        return id;
    }

    /** @return related order identifier */
    public UUID getOrderId() {
        return orderId;
    }

    /** @return payment amount */
    public BigDecimal getAmount() {
        return amount;
    }

    /** @return external transaction identifier from payment provider */
    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    /** @return redirect URL used to continue payment at provider side */
    public String getRedirectUrl() {
        return redirectUrl;
    }

    /** @return payment creation timestamp */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return current payment status */
    public PaymentStatus getStatus() {
        return status;
    }

    /** @return {@code true} when current status is {@code PAID} */
    public boolean isPaid() {
        return this.status == PaymentStatus.PAID;
    }

}
