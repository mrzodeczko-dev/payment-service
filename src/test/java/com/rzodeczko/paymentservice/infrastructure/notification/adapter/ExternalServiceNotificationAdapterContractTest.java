//package com.rzodeczko.paymentservice.infrastructure.notification.adapter;
//
//import com.rzodeczko.paymentservice.infrastructure.configuration.properties.TPayProperties;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.RegisterExtension;
//import org.springframework.cloud.contract.stubrunner.junit.StubRunnerExtension;
//import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
//import org.springframework.web.client.RestClient;
//
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThatCode;
//
///**
// * Consumer-side contract test: Payment Service → Order Service.
// */
//class ExternalServiceNotificationAdapterContractTest {
//
//    @RegisterExtension
//    static StubRunnerExtension stubRunner = new StubRunnerExtension()
//            .downloadStub("com.rzodeczko", "order-service")
//            .withPort(0)
//            .stubsMode(StubRunnerProperties.StubsMode.REMOTE)
//            .repoRoot("https://maven.pkg.github.com/mrzodeczko-dev/order-service");
//
//    private ExternalServiceNotificationAdapter adapter;
//
//    @BeforeEach
//    void setup() {
//        int port = stubRunner.findStubUrl("com.rzodeczko", "order-service").getPort();
//        String baseUrl = "http://localhost:" + port;
//
//        TPayProperties properties = new TPayProperties(
//                new TPayProperties.Api("http://unused", "id", "secret", "code"),
//                new TPayProperties.App("http://n", "http://n", "http://n", baseUrl)
//        );
//
//        adapter = new ExternalServiceNotificationAdapter(RestClient.builder(), properties);
//    }
//
//    @Test
//    void shouldNotifyOrderServiceAgainstStub() {
//        // when/then — contract guarantees 200 OK
//        assertThatCode(() -> adapter.notifyExternalService(UUID.randomUUID(), UUID.randomUUID()))
//                .doesNotThrowAnyException();
//    }
//}
