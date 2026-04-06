package com.rzodeczko.paymentservice.infrastructure.persistence.mapper;


import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.model.PaymentStatus;
import com.rzodeczko.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

/**
 * Maps payment aggregates between the domain model and the JPA persistence model.
 *
 * <p>This mapper keeps conversion rules in one place so repository adapters can focus on
 * storage concerns instead of object translation.</p>
 */
@Component
public class PaymentMapper {

    public PaymentEntity toEntity(Payment domain) {
        return PaymentEntity
                .builder()
                .id(domain.getId())
                .orderId(domain.getOrderId())
                .amount(domain.getAmount())
                .status(domain.getStatus().name())
                .externalTransactionId(domain.getExternalTransactionId())
                .redirectUrl(domain.getRedirectUrl())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public Payment toDomain(PaymentEntity entity) {
        return new Payment(
                entity.getId(),
                entity.getOrderId(),
                entity.getAmount(),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getExternalTransactionId(),
                entity.getRedirectUrl(),
                entity.getCreatedAt()
        );
    }
}
