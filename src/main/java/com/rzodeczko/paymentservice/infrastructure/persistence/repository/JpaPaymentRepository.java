package com.rzodeczko.paymentservice.infrastructure.persistence.repository;

import com.rzodeczko.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PaymentEntity} persistence operations.
 *
 * <p>Provides database access by internal order identifier and by external gateway
 * transaction identifier.</p>
 */
public interface JpaPaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByExternalTransactionId(String externalTransactionId);
    Optional<PaymentEntity> findByOrderId(UUID orderId);
}
