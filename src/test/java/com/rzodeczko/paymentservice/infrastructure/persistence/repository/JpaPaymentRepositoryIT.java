package com.rzodeczko.paymentservice.infrastructure.persistence.repository;

import com.rzodeczko.paymentservice.BaseIntegrationTest;
import com.rzodeczko.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.rzodeczko.paymentservice.application.port.output.PaymentGatewayPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for JpaPaymentRepository running against real MySQL via Testcontainers.
 * Verifies Liquibase migrations + JPA mappings + custom queries work correctly.
 */
@AutoConfigureMockMvc
class JpaPaymentRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private JpaPaymentRepository jpaPaymentRepository;

    @Autowired
    private JpaOutboxEventRepository jpaOutboxEventRepository;

    @MockitoBean
    private PaymentGatewayPort paymentGatewayPort;

    @BeforeEach
    void cleanUp() {
        jpaOutboxEventRepository.deleteAll();
        jpaPaymentRepository.deleteAll();
    }

    @Test
    void shouldSaveAndFindPaymentById() {
        // given
        UUID id = UUID.randomUUID();
        PaymentEntity entity = PaymentEntity.builder()
                .id(id)
                .orderId(UUID.randomUUID())
                .externalTransactionId("ext-" + id)
                .amount(new BigDecimal("150.00"))
                .status("PENDING")
                .redirectUrl("https://pay.example.com/redirect")
                .createdAt(Instant.now())
                .build();

        // when
        jpaPaymentRepository.save(entity);
        Optional<PaymentEntity> found = jpaPaymentRepository.findById(id);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo(entity.getOrderId());
        assertThat(found.get().getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(found.get().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldFindByOrderId() {
        // given
        UUID orderId = UUID.randomUUID();
        PaymentEntity entity = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .externalTransactionId("ext-order-" + orderId)
                .amount(new BigDecimal("200.00"))
                .status("PENDING")
                .redirectUrl("https://pay.example.com")
                .createdAt(Instant.now())
                .build();
        jpaPaymentRepository.save(entity);

        // when
        Optional<PaymentEntity> found = jpaPaymentRepository.findByOrderId(orderId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo(orderId);
    }

    @Test
    void shouldFindByExternalTransactionId() {
        // given
        String extId = "ext-tx-" + UUID.randomUUID();
        PaymentEntity entity = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .externalTransactionId(extId)
                .amount(new BigDecimal("50.00"))
                .status("PAID")
                .redirectUrl("https://pay.example.com")
                .createdAt(Instant.now())
                .build();
        jpaPaymentRepository.save(entity);

        // when
        Optional<PaymentEntity> found = jpaPaymentRepository.findByExternalTransactionId(extId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getExternalTransactionId()).isEqualTo(extId);
    }

    @Test
    void shouldReturnEmptyWhenPaymentNotFound() {
        // when
        Optional<PaymentEntity> found = jpaPaymentRepository.findByOrderId(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdatePaymentStatus() {
        // given
        UUID id = UUID.randomUUID();
        PaymentEntity entity = PaymentEntity.builder()
                .id(id)
                .orderId(UUID.randomUUID())
                .externalTransactionId("ext-update-" + id)
                .amount(new BigDecimal("100.00"))
                .status("PENDING")
                .redirectUrl("https://pay.example.com")
                .createdAt(Instant.now())
                .build();
        jpaPaymentRepository.save(entity);

        // when
        entity.setStatus("PAID");
        jpaPaymentRepository.save(entity);

        // then
        PaymentEntity updated = jpaPaymentRepository.findById(id).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("PAID");
    }
}
