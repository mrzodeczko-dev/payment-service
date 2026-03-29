package com.rzodeczko.paymentservice.domain.model;

import java.time.Instant;
import java.util.UUID;

public class OutboxEvent {
    private final UUID id;
    private final UUID orderId;
    private final UUID paymentId;
    private OutboxEventStatus status;
    private int retryCount;
    private final Instant createdAt;
    private Instant processedAt;

    private static final int MAX_RETRY_COUNT = 5;

    public OutboxEvent(
            UUID id,
            UUID orderId,
            UUID paymentId,
            OutboxEventStatus status,
            int retryCount,
            Instant createdAt,
            Instant processedAt
    ) {
        this.id = id;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.status = status;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public static OutboxEvent create(UUID orderId, UUID paymentId) {
        return new OutboxEvent(
                UUID.randomUUID(),
                orderId,
                paymentId,
                OutboxEventStatus.PENDING,
                0,
                Instant.now(),
                null
        );
    }

    public void markSent() {
        this.status = OutboxEventStatus.SENT;
        this.processedAt = Instant.now();
    }

    public void markFailed() {
        ++this.retryCount;
        if (this.retryCount >= 5) {
            this.status = OutboxEventStatus.FAILED;
        }
    }
}
