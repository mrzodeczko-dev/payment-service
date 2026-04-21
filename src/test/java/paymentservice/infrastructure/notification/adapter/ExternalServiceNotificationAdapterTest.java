package paymentservice.infrastructure.notification.adapter;

import com.rzodeczko.paymentservice.infrastructure.configuration.properties.TPayProperties;
import com.rzodeczko.paymentservice.infrastructure.notification.adapter.ExternalServiceNotificationAdapter;
import com.rzodeczko.paymentservice.infrastructure.notification.dto.PaymentConfirmationRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalServiceNotificationAdapterTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private TPayProperties properties;

    @Mock
    private TPayProperties.App app;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private ResponseEntity<Void> responseEntity;

    @Test
    void notifyExternalService_shouldSendNotificationSuccessfully() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        when(properties.app()).thenReturn(app);
        when(app.externalServiceUrl()).thenReturn("http://example.com");
        when(restClientBuilder.baseUrl("http://example.com")).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/orders/{orderId}/payment", orderId)).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(PaymentConfirmationRequestDto.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(responseEntity);

        ExternalServiceNotificationAdapter adapter = new ExternalServiceNotificationAdapter(restClientBuilder, properties);

        // when & then
        assertThatCode(() -> adapter.notifyExternalService(orderId, paymentId))
                .doesNotThrowAnyException();
    }

    @Test
    void notifyExternalService_shouldThrowIllegalStateException_whenRestClientFails() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        when(properties.app()).thenReturn(app);
        when(app.externalServiceUrl()).thenReturn("http://example.com");
        when(restClientBuilder.baseUrl("http://example.com")).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/orders/{orderId}/payment", orderId)).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(PaymentConfirmationRequestDto.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(new RestClientException("Network error"));

        ExternalServiceNotificationAdapter adapter = new ExternalServiceNotificationAdapter(restClientBuilder, properties);

        // when & then
        assertThatThrownBy(() -> adapter.notifyExternalService(orderId, paymentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to notify external service with orderId: " + orderId);
    }
}
