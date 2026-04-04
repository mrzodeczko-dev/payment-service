package com.rzodeczko.paymentservice.domain.repository;

import com.rzodeczko.paymentservice.domain.model.OutboxEvent;

import java.util.List;

/**
 * Domain-level repository port for persisting and retrieving
 * {@link OutboxEvent} records used by the outbox pattern.
 *
 * <p>Infrastructure adapters implement this contract to provide reliable
 * event storage and subsequent dispatch processing.</p>
 */
public interface OutboxEventRepository {

    /**
     * Persists the provided outbox event.
     *
     * @param event outbox event to persist
     */
    void save(OutboxEvent event);

    /**
     * Retrieves all outbox events that are still pending dispatch.
     *
     * @return list of pending outbox events eligible for publishing
     */
    List<OutboxEvent> findAllPending();
}
