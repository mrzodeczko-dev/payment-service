package com.rzodeczko.paymentservice.infrastructure.outbox;

import com.rzodeczko.paymentservice.domain.model.OutboxEvent;
import com.rzodeczko.paymentservice.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class OutboxProcessorTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final OutboxEventSender outboxEventSender = mock(OutboxEventSender.class);
    private final OutboxProcessor processor = new OutboxProcessor(outboxEventRepository, outboxEventSender);

    @Test
    void process_shouldDoNothing_whenNoPendingEvents() {
        when(outboxEventRepository.findPending(anyInt())).thenReturn(Collections.emptyList());

        processor.process();

        verify(outboxEventSender, never()).send(any());
    }

    @Test
    void process_shouldSendEachPendingEvent() {
        OutboxEvent e1 = OutboxEvent.create(UUID.randomUUID(), UUID.randomUUID());
        OutboxEvent e2 = OutboxEvent.create(UUID.randomUUID(), UUID.randomUUID());
        when(outboxEventRepository.findPending(anyInt())).thenReturn(List.of(e1, e2));

        processor.process();

        verify(outboxEventSender).send(e1);
        verify(outboxEventSender).send(e2);
    }
}
