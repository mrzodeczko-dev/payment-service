package com.rzodeczko.paymentservice.infrastructure.persistence.mapper;


import com.rzodeczko.paymentservice.domain.model.OutboxEvent;
import com.rzodeczko.paymentservice.domain.model.OutboxEventStatus;
import com.rzodeczko.paymentservice.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.stereotype.Component;

/**
 * Maps outbox event aggregates between the domain layer and JPA persistence entities.
 *
 * <p>Used by infrastructure adapters to translate delivery state without leaking
 * persistence concerns into the domain model.</p>
 */
@Component
public class OutboxEventMapper {
    public OutboxEventEntity toEntity(OutboxEvent domain) {
        return OutboxEventEntity
                .builder()
                .id(domain.getId())
                .orderId(domain.getOrderId())
                .paymentId(domain.getPaymentId())
                .status(domain.getStatus().name())
                .retryCount(domain.getRetryCount())
                .createdAt(domain.getCreatedAt())
                .processedAt(domain.getProcessedAt())
                .build();
    }

    public OutboxEvent toDomain(OutboxEventEntity entity) {
        return new OutboxEvent(
                entity.getId(),
                entity.getOrderId(),
                entity.getPaymentId(),
                OutboxEventStatus.valueOf(entity.getStatus()),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getProcessedAt()
        );
    }
}
