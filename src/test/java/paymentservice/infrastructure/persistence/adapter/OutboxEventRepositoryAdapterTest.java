package paymentservice.infrastructure.persistence.adapter;

import com.rzodeczko.paymentservice.domain.model.OutboxEvent;
import com.rzodeczko.paymentservice.domain.model.OutboxEventStatus;
import com.rzodeczko.paymentservice.infrastructure.persistence.adapter.OutboxEventRepositoryAdapter;
import com.rzodeczko.paymentservice.infrastructure.persistence.entity.OutboxEventEntity;
import com.rzodeczko.paymentservice.infrastructure.persistence.mapper.OutboxEventMapper;
import com.rzodeczko.paymentservice.infrastructure.persistence.repository.JpaOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventRepositoryAdapterTest {

    @Mock
    private JpaOutboxEventRepository jpaOutboxEventRepository;

    @Mock
    private OutboxEventMapper outboxEventMapper;

    @InjectMocks
    private OutboxEventRepositoryAdapter repositoryAdapter;

    @Test
    void save_shouldUpdateExistingEntity_whenEventAlreadyExists() {
        // given
        UUID id = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                OutboxEventStatus.SENT,
                2,
                Instant.now().minusSeconds(60),
                Instant.now()
        );

        OutboxEventEntity existingEntity = OutboxEventEntity.builder()
                .id(id)
                .orderId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .status(OutboxEventStatus.PENDING.name())
                .retryCount(0)
                .createdAt(Instant.now().minusSeconds(120))
                .processedAt(null)
                .build();

        when(jpaOutboxEventRepository.findById(id)).thenReturn(Optional.of(existingEntity));

        // when
        repositoryAdapter.save(event);

        // then
        assertThat(existingEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT.name());
        assertThat(existingEntity.getRetryCount()).isEqualTo(2);
        assertThat(existingEntity.getProcessedAt()).isEqualTo(event.getProcessedAt());

        verify(jpaOutboxEventRepository).findById(id);
        verify(outboxEventMapper, never()).toEntity(event);
        verify(jpaOutboxEventRepository, never()).saveAndFlush(existingEntity);
    }

    @Test
    void save_shouldCreateEntity_whenEventDoesNotExist() {
        // given
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                OutboxEventStatus.PENDING,
                0,
                Instant.now(),
                null
        );

        OutboxEventEntity mappedEntity = OutboxEventEntity.builder()
                .id(event.getId())
                .orderId(event.getOrderId())
                .paymentId(event.getPaymentId())
                .status(event.getStatus().name())
                .retryCount(event.getRetryCount())
                .createdAt(event.getCreatedAt())
                .processedAt(event.getProcessedAt())
                .build();

        when(jpaOutboxEventRepository.findById(event.getId())).thenReturn(Optional.empty());
        when(outboxEventMapper.toEntity(event)).thenReturn(mappedEntity);

        // when
        repositoryAdapter.save(event);

        // then
        verify(jpaOutboxEventRepository).findById(event.getId());
        verify(outboxEventMapper).toEntity(event);
        verify(jpaOutboxEventRepository).saveAndFlush(mappedEntity);
    }

    @Test
    void findAllPending_shouldReturnMappedDomainEvents() {
        // given
        OutboxEventEntity firstEntity = buildEntity(OutboxEventStatus.PENDING.name());
        OutboxEventEntity secondEntity = buildEntity(OutboxEventStatus.PENDING.name());

        OutboxEvent firstDomain = buildDomain(OutboxEventStatus.PENDING);
        OutboxEvent secondDomain = buildDomain(OutboxEventStatus.PENDING);

        when(jpaOutboxEventRepository.findAllByStatus(OutboxEventStatus.PENDING.name()))
                .thenReturn(List.of(firstEntity, secondEntity));
        when(outboxEventMapper.toDomain(firstEntity)).thenReturn(firstDomain);
        when(outboxEventMapper.toDomain(secondEntity)).thenReturn(secondDomain);

        // when
        List<OutboxEvent> result = repositoryAdapter.findAllPending();

        // then
        assertThat(result).containsExactly(firstDomain, secondDomain);
        verify(jpaOutboxEventRepository).findAllByStatus(OutboxEventStatus.PENDING.name());
        verify(outboxEventMapper).toDomain(firstEntity);
        verify(outboxEventMapper).toDomain(secondEntity);
    }

    @Test
    void findAllPending_shouldReturnEmptyList_whenNoPendingEventsExist() {
        // given
        when(jpaOutboxEventRepository.findAllByStatus(OutboxEventStatus.PENDING.name()))
                .thenReturn(List.of());

        // when
        List<OutboxEvent> result = repositoryAdapter.findAllPending();

        // then
        assertThat(result).isEmpty();
        verify(jpaOutboxEventRepository).findAllByStatus(OutboxEventStatus.PENDING.name());
        verify(outboxEventMapper, never()).toDomain(org.mockito.ArgumentMatchers.any(OutboxEventEntity.class));
    }

    @Test
    void findPending_shouldReturnMappedDomainEventsAndUsePageRequestWithAscendingCreatedAtSort() {
        // given
        int limit = 5;
        OutboxEventEntity firstEntity = buildEntity(OutboxEventStatus.PENDING.name());
        OutboxEventEntity secondEntity = buildEntity(OutboxEventStatus.PENDING.name());
        OutboxEvent firstDomain = buildDomain(OutboxEventStatus.PENDING);
        OutboxEvent secondDomain = buildDomain(OutboxEventStatus.PENDING);

        when(jpaOutboxEventRepository.findByStatus(eq(OutboxEventStatus.PENDING.name()), any(PageRequest.class)))
                .thenReturn(List.of(firstEntity, secondEntity));
        when(outboxEventMapper.toDomain(firstEntity)).thenReturn(firstDomain);
        when(outboxEventMapper.toDomain(secondEntity)).thenReturn(secondDomain);

        // when
        List<OutboxEvent> result = repositoryAdapter.findPending(limit);

        // then
        assertThat(result).containsExactly(firstDomain, secondDomain);
        verify(jpaOutboxEventRepository).findByStatus(eq(OutboxEventStatus.PENDING.name()), argThat(pageRequest ->
                pageRequest.getPageNumber() == 0
                        && pageRequest.getPageSize() == limit
                        && pageRequest.getSort().equals(Sort.by("createdAt").ascending())
        ));
        verify(outboxEventMapper).toDomain(firstEntity);
        verify(outboxEventMapper).toDomain(secondEntity);
    }

    @Test
    void findPending_shouldReturnEmptyList_whenRepositoryReturnsNoPendingEvents() {
        // given
        int limit = 3;
        when(jpaOutboxEventRepository.findByStatus(eq(OutboxEventStatus.PENDING.name()), any(PageRequest.class)))
                .thenReturn(List.of());

        // when
        List<OutboxEvent> result = repositoryAdapter.findPending(limit);

        // then
        assertThat(result).isEmpty();
        verify(jpaOutboxEventRepository).findByStatus(eq(OutboxEventStatus.PENDING.name()), any(PageRequest.class));
        verify(outboxEventMapper, never()).toDomain(any(OutboxEventEntity.class));
    }

    private OutboxEventEntity buildEntity(String status) {
        return OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .status(status)
                .retryCount(0)
                .createdAt(Instant.now())
                .processedAt(null)
                .build();
    }

    private OutboxEvent buildDomain(OutboxEventStatus status) {
        return new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                0,
                Instant.now(),
                null
        );
    }
}

