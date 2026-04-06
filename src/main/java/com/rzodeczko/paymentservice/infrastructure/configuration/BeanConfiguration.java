package com.rzodeczko.paymentservice.infrastructure.configuration;

import com.rzodeczko.paymentservice.application.port.output.PaymentGatewayPort;
import com.rzodeczko.paymentservice.application.service.PaymentService;
import com.rzodeczko.paymentservice.domain.repository.OutboxEventRepository;
import com.rzodeczko.paymentservice.domain.repository.PaymentRepository;
import com.rzodeczko.paymentservice.infrastructure.configuration.properties.TPayProperties;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Spring infrastructure configuration.
 *
 * <p>Registers application-wide beans for outbound HTTP communication and distributed
 * scheduler locking. Configuration properties for TPay integration are enabled here as well.</p>
 */
@Configuration
@EnableConfigurationProperties({TPayProperties.class})
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class BeanConfiguration {

    /**
     * Creates a ShedLock {@link LockProvider} backed by the application's main database.
     *
     * <p>The provider stores lock state in the {@code shedlock} table and uses database time
     * ({@code usingDbTime}) instead of node-local JVM time, which avoids clock-skew issues
     * across multiple service instances.</p>
     *
     * @param jdbcTemplate JDBC template connected to the primary application datasource
     * @return JDBC-based ShedLock provider
     */
    @Bean
    public LockProvider lockProvider(JdbcTemplate jdbcTemplate) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .usingDbTime()
                .build());
    }


    /**
     * Configures a shared {@code RestClientCustomizer} backed by JDK HttpClient
     * with connection/read timeouts and a virtual-thread executor.
     *
     * @return customizer that applies a request factory to all RestClient builders
     */
    @Bean
    public RestClientCustomizer restClientCustomizer() {
        HttpClient httpClient = HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofMillis(2000))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(5000));

        return builder -> builder.requestFactory(requestFactory);
    }

    /**
     * Creates the core {@link PaymentService} business logic component.
     *
     * <p>The service coordinates payment lifecycle operations by interacting with:
     * <ul>
     *   <li>Payment repository for persistence operations</li>
     *   <li>Payment gateway port for external payment processing</li>
     *   <li>Outbox event repository for reliable event publishing</li>
     * </ul>
     * </p>
     *
     * @param paymentRepository repository for storing and retrieving payment entities
     * @param paymentGatewayPort adapter for external payment gateway integration
     * @param outboxEventRepository repository for persisting outbox events used in event-driven architecture
     * @return configured PaymentService instance
     */
    @Bean
    public PaymentService paymentService(
            PaymentRepository paymentRepository,
            PaymentGatewayPort paymentGatewayPort,
            OutboxEventRepository outboxEventRepository
    ) {

        return new PaymentService(
                paymentRepository,
                paymentGatewayPort,
                outboxEventRepository);

    }
}
