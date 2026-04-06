package com.rzodeczko.paymentservice.infrastructure.persistence.adapter;

import com.rzodeczko.paymentservice.domain.exception.PaymentAlreadyExistsException;
import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.repository.PaymentRepository;
import com.rzodeczko.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import com.rzodeczko.paymentservice.infrastructure.persistence.mapper.PaymentMapper;
import com.rzodeczko.paymentservice.infrastructure.persistence.repository.JpaPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed infrastructure adapter implementing the domain {@link PaymentRepository} port.
 *
 * <p>The adapter owns two responsibilities at the infrastructure boundary:
 * mapping between {@link Payment} aggregates and {@link PaymentEntity} persistence models,
 * and translating storage-level exceptions into domain-level exceptions.</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {
    private final JpaPaymentRepository jpaPaymentRepository;
    private final PaymentMapper paymentMapper;

    /**
     * Persists a payment aggregate using upsert-like semantics keyed by payment identifier.
     *
     * <p>If an entity with the same {@code payment.id} already exists, only its status is updated.
     * Otherwise a new entity is created from the aggregate. The operation uses
     * {@code saveAndFlush(...)} to force SQL execution within this method, so constraint violations
     * are raised in this adapter and can be translated deterministically.</p>
     *
     * @param payment payment aggregate to persist
     * @return persisted payment aggregate mapped back from the database entity
     * @throws PaymentAlreadyExistsException when a uniqueness or integrity constraint is violated
     */
    @Override
    @Transactional
    public Payment save(Payment payment) {
        try {
            PaymentEntity entity = jpaPaymentRepository.findById(payment.getId())
                    .map(existing -> {
                        existing.setStatus(payment.getStatus().name());
                        return existing;
                    })
                    .orElseGet(() -> paymentMapper.toEntity(payment));
            return paymentMapper.toDomain(
                    jpaPaymentRepository.saveAndFlush(entity)
            );
        } catch (DataIntegrityViolationException e) {
            throw new PaymentAlreadyExistsException(payment.getOrderId());
        }
    }


    /**
     * Retrieves a payment by provider-assigned external transaction identifier.
     *
     * @param externalTransactionId transaction identifier issued by the payment gateway
     * @return {@link Optional#empty()} when no payment is associated with the given identifier;
     * otherwise an optional containing the mapped domain aggregate
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByExternalTransactionId(String externalTransactionId) {
        return jpaPaymentRepository
                .findByExternalTransactionId(externalTransactionId)
                .map(paymentMapper::toDomain);
    }

    /**
     * Retrieves a payment by internal order identifier.
     *
     * @param orderId application-level order identifier correlated with the payment
     * @return {@link Optional#empty()} when no payment exists for the order;
     * otherwise an optional containing the mapped domain aggregate
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByOrderId(UUID orderId) {
        return jpaPaymentRepository
                .findByOrderId(orderId)
                .map(paymentMapper::toDomain);
    }
}
