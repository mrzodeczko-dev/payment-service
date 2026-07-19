package com.rzodeczko.paymentservice.infrastructure.outbox;

import com.rzodeczko.paymentservice.application.port.output.NotificationPort;
import com.rzodeczko.paymentservice.domain.model.OutboxEvent;
import com.rzodeczko.paymentservice.domain.model.OutboxEventStatus;
import com.rzodeczko.paymentservice.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxEventSenderTest {

    private final NotificationPort notificationPort = mock(NotificationPort.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final OutboxEventSender sender = new OutboxEventSender(notificationPort, outboxEventRepository);

    @Test
    void send_shouldMarkSentAndSave_whenNotificationSucceeds() {
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), UUID.randomUUID());

        sender.send(event);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(event.getProcessedAt()).isNotNull();
        verify(notificationPort).notifyExternalService(event.getOrderId(), event.getPaymentId());
        verify(outboxEventRepository).save(event);
    }

    @Test
    void send_shouldMarkFailedAndSave_whenNotificationThrows() {
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), UUID.randomUUID());
        doThrow(new RuntimeException("connection refused"))
                .when(notificationPort).notifyExternalService(any(), any());

        sender.send(event);

        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING); // still under max
        verify(outboxEventRepository).save(event);
    }
}
