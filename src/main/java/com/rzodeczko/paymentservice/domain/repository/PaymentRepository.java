package com.rzodeczko.paymentservice.domain.repository;

import com.rzodeczko.paymentservice.domain.model.Payment;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain-level repository port for persisting and retrieving {@link Payment}
 * aggregates.
 *
 * <p>In the hexagonal architecture used by this service, repository interfaces
 * belong to the domain contract, while infrastructure adapters provide concrete
 * persistence implementations.</p>
 */
public interface PaymentRepository {

    /**
     * Persists the provided payment aggregate.
     *
     * @param payment payment aggregate to persist
     * @return persisted payment aggregate state
     */
    Payment save(Payment payment);

    /**
     * Retrieves a payment by the gateway-side transaction identifier.
     *
     * @param externalTransactionId external transaction identifier assigned by the payment provider
     * @return {@link Optional} with the matching payment, or empty when no payment exists for this identifier
     */
    Optional<Payment> findByExternalTransactionId(String externalTransactionId);

    /**
     * Retrieves a payment by the internal order identifier.
     *
     * @param orderId internal order identifier associated with a payment attempt
     * @return {@link Optional} with the matching payment, or empty when no payment exists for this order
     */
    Optional<Payment> findByOrderId(UUID orderId);
}
