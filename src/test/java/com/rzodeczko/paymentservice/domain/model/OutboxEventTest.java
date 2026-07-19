package com.rzodeczko.paymentservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void create_shouldReturnPendingEventWithZeroRetries() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        OutboxEvent event = OutboxEvent.create(orderId, paymentId);

        assertThat(event.getId()).isNotNull();
        assertThat(event.getOrderId()).isEqualTo(orderId);
        assertThat(event.getPaymentId()).isEqualTo(paymentId);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getProcessedAt()).isNull();
    }

    @Test
    void markSent_shouldSetStatusToSentAndTimestamp() {
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), UUID.randomUUID());

        event.markSent();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    void markFailed_shouldIncrementRetryCount() {
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), UUID.randomUUID());

        event.markFailed();

        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void markFailed_shouldSetStatusToFailed_whenMaxRetriesReached() {
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), UUID.randomUUID());

        for (int i = 0; i < 5; i++) {
            event.markFailed();
        }

        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    @Test
    void markFailed_shouldRemainPending_beforeMaxRetries() {
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), UUID.randomUUID());

        for (int i = 0; i < 4; i++) {
            event.markFailed();
        }

        assertThat(event.getRetryCount()).isEqualTo(4);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void constructor_shouldSetAllFields() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();

        OutboxEvent event = new OutboxEvent(id, orderId, paymentId,
                OutboxEventStatus.SENT, 3, now, now);

        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getOrderId()).isEqualTo(orderId);
        assertThat(event.getPaymentId()).isEqualTo(paymentId);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getCreatedAt()).isEqualTo(now);
        assertThat(event.getProcessedAt()).isEqualTo(now);
    }
}
