package com.rzodeczko.paymentservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Payment {
    private final UUID id;
    private final UUID orderId;
    private final BigDecimal amount;

    private final String externalTransactionId; /*constraint uniqui id w bazie*/
    private final String redirectUrl;
    private final Instant createdAt;
    private PaymentStatus status;

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
        this.status = PaymentStatus.PENDING;
        this.redirectUrl = redirectUrl;
        this.createdAt = createdAt;
    }

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

    public void confirm() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Cannot confirm payment in status: %s. Expected: Pending".formatted(this.status));
        }
        this.status = PaymentStatus.PAID;
    }

    public void fail() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Cannot fail payment in status: %s. Expected: Pending".formatted(this.status));
        }
        this.status = PaymentStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
