package com.rzodeczko.paymentservice.infrastructure.persistence.repository;

import com.rzodeczko.paymentservice.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEventEntity} records.
 *
 * <p>Used by the outbox workflow to fetch pending events and to persist delivery
 * progress across retry attempts.</p>
 */
public interface JpaOutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findAllByStatus(String status);

    List<OutboxEventEntity> findByStatus(String status, PageRequest pageRequest);
}
