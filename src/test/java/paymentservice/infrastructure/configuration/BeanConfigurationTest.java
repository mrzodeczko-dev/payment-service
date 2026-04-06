package paymentservice.infrastructure.configuration;

import com.rzodeczko.paymentservice.application.service.PaymentService;
import com.rzodeczko.paymentservice.domain.repository.OutboxEventRepository;
import com.rzodeczko.paymentservice.domain.repository.PaymentRepository;
import com.rzodeczko.paymentservice.infrastructure.configuration.BeanConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class BeanConfigurationTest {

    private final BeanConfiguration beanConfiguration = new BeanConfiguration();

    @Test
    void lockProvider_shouldCreateJdbcTemplateLockProvider() {
        DataSource dataSource = Mockito.mock(DataSource.class);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        LockProvider lockProvider = beanConfiguration.lockProvider(jdbcTemplate);

        assertThat(lockProvider).isInstanceOf(JdbcTemplateLockProvider.class);
    }

    @Test
    void restClientCustomizer_shouldApplyRequestFactoryToBuilder() {
        RestClientCustomizer customizer = beanConfiguration.restClientCustomizer();

        RestClient.Builder builder = RestClient.builder();
        customizer.customize(builder);
        RestClient restClient = builder.build();

        assertThat(restClient).isNotNull();
    }

    @Test
    void paymentService_shouldCreateServiceWithProvidedRepositories() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        OutboxEventRepository outboxEventRepository = Mockito.mock(OutboxEventRepository.class);

        PaymentService service = beanConfiguration.paymentService(paymentRepository, outboxEventRepository);

        assertThat(service).isNotNull();
    }
}


