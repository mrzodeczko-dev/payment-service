package com.rzodeczko.paymentservice.infrastructure.persistence.mapper;

import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.model.PaymentStatus;
import com.rzodeczko.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapper();

    @Test
    void toEntity_shouldMapAllFields() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        Payment domain = new Payment(id, orderId, new BigDecimal("150.00"),
                PaymentStatus.PAID, "ext-1", "https://pay.com", now);

        PaymentEntity entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getOrderId()).isEqualTo(orderId);
        assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(entity.getStatus()).isEqualTo("PAID");
        assertThat(entity.getExternalTransactionId()).isEqualTo("ext-1");
        assertThat(entity.getRedirectUrl()).isEqualTo("https://pay.com");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toDomain_shouldMapAllFields() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        PaymentEntity entity = PaymentEntity.builder()
                .id(id)
                .orderId(orderId)
                .amount(new BigDecimal("200.00"))
                .status("REFUNDED")
                .externalTransactionId("ext-2")
                .redirectUrl("https://redirect.com")
                .createdAt(now)
                .build();

        Payment domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(id);
        assertThat(domain.getOrderId()).isEqualTo(orderId);
        assertThat(domain.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(domain.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(domain.getExternalTransactionId()).isEqualTo("ext-2");
        assertThat(domain.getRedirectUrl()).isEqualTo("https://redirect.com");
        assertThat(domain.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void roundTrip_shouldPreserveData() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        Payment original = new Payment(id, orderId, new BigDecimal("50.00"),
                PaymentStatus.PENDING, "ext-rt", "https://rt.com", now);

        Payment roundTripped = mapper.toDomain(mapper.toEntity(original));

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getOrderId()).isEqualTo(original.getOrderId());
        assertThat(roundTripped.getAmount()).isEqualByComparingTo(original.getAmount());
        assertThat(roundTripped.getStatus()).isEqualTo(original.getStatus());
        assertThat(roundTripped.getExternalTransactionId()).isEqualTo(original.getExternalTransactionId());
        assertThat(roundTripped.getRedirectUrl()).isEqualTo(original.getRedirectUrl());
    }
}
