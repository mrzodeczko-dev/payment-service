package com.rzodeczko.paymentservice.infrastructure.notification.adapter;

import com.rzodeczko.paymentservice.application.port.output.NotificationPort;
import com.rzodeczko.paymentservice.infrastructure.configuration.properties.TPayProperties;
import com.rzodeczko.paymentservice.infrastructure.notification.dto.PaymentConfirmationRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Infrastructure adapter implementing {@link NotificationPort} for outbound payment notifications.
 *
 * <p>The current implementation is intentionally minimal and logs notification attempts only.
 * It acts as a placeholder seam for the final integration (for example an HTTP API call or
 * asynchronous event publication) that will be introduced in later iterations.</p>
 */
@Component
@Slf4j
public class ExternalServiceNotificationAdapter implements NotificationPort {

    private final RestClient restClient;

    public ExternalServiceNotificationAdapter(
            RestClient.Builder restClientBuilder,
            TPayProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.app().externalServiceUrl())
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This placeholder implementation performs structured logging and does not execute
     * a remote call yet.</p>
     */
    @Override
    public void notifyExternalService(UUID orderId, UUID paymentId) {
        log.info("Notifying external service with orderId: {} and paymentId: {}", orderId, paymentId);
        try {
            restClient.post()
                    .uri("/orders/{orderId}/payment", orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaymentConfirmationRequestDto(paymentId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("External service notified. orderId={}, paymentId={}", orderId, paymentId);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to notify external service with orderId: " + orderId, e);
        }
    }
}
