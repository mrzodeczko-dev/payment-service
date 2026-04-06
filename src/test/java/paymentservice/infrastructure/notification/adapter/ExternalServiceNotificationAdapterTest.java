package paymentservice.infrastructure.notification.adapter;

import com.rzodeczko.paymentservice.infrastructure.notification.adapter.ExternalServiceNotificationAdapter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class ExternalServiceNotificationAdapterTest {

    private final ExternalServiceNotificationAdapter adapter = new ExternalServiceNotificationAdapter();

    @Test
    void notifyExternalService_shouldNotThrowException() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        assertThatCode(() -> adapter.notifyExternalService(orderId, paymentId))
                .doesNotThrowAnyException();
    }
}

