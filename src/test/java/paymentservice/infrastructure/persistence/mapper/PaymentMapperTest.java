package paymentservice.infrastructure.persistence.mapper;

import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.model.PaymentStatus;
import com.rzodeczko.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import com.rzodeczko.paymentservice.infrastructure.persistence.mapper.PaymentMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapper();

    // --- toEntity ---

    @Test
    void toEntity_shouldMapAllFields() {
        // given
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        Payment domain = new Payment(
                id, orderId,
                new BigDecimal("149.99"),
                PaymentStatus.PENDING,
                "ext-tx-001",
                "https://gateway.example.com/pay",
                createdAt
        );

        // when
        PaymentEntity entity = mapper.toEntity(domain);

        // then
        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getOrderId()).isEqualTo(orderId);
        assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("149.99"));
        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getExternalTransactionId()).isEqualTo("ext-tx-001");
        assertThat(entity.getRedirectUrl()).isEqualTo("https://gateway.example.com/pay");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void toEntity_shouldSerializeStatusAsEnumName() {
        // given
        Payment paid = buildPayment(PaymentStatus.PAID);
        Payment failed = buildPayment(PaymentStatus.FAILED);

        // when & then
        assertThat(mapper.toEntity(paid).getStatus()).isEqualTo("PAID");
        assertThat(mapper.toEntity(failed).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void toEntity_shouldMapNullRedirectUrl() {
        // given
        Payment domain = new Payment(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("50.00"),
                PaymentStatus.PENDING,
                "ext-tx-002",
                null,
                Instant.now()
        );

        // when
        PaymentEntity entity = mapper.toEntity(domain);

        // then
        assertThat(entity.getRedirectUrl()).isNull();
    }


    // --- toDomain ---

    @Test
    void toDomain_shouldMapAllFields() {
        // given
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        PaymentEntity entity = PaymentEntity.builder()
                .id(id)
                .orderId(orderId)
                .amount(new BigDecimal("149.99"))
                .status("PAID")
                .externalTransactionId("ext-tx-003")
                .redirectUrl("https://gateway.example.com/pay")
                .createdAt(createdAt)
                .build();

        // when
        Payment domain = mapper.toDomain(entity);

        // then
        assertThat(domain.getId()).isEqualTo(id);
        assertThat(domain.getOrderId()).isEqualTo(orderId);
        assertThat(domain.getAmount()).isEqualByComparingTo(new BigDecimal("149.99"));
        assertThat(domain.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(domain.getExternalTransactionId()).isEqualTo("ext-tx-003");
        assertThat(domain.getRedirectUrl()).isEqualTo("https://gateway.example.com/pay");
        assertThat(domain.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void toDomain_shouldDeserializeStatusFromString() {
        // given
        PaymentEntity pending = buildEntity("PENDING");
        PaymentEntity failed = buildEntity("FAILED");

        // when & then
        assertThat(mapper.toDomain(pending).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(mapper.toDomain(failed).getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // --- round-trip ---

    @Test
    void toEntity_andToDomain_shouldProduceEquivalentObject() {
        // given
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        Payment original = new Payment(
                id, orderId,
                new BigDecimal("99.99"),
                PaymentStatus.PENDING,
                "ext-tx-rt",
                "https://gateway.example.com/pay",
                createdAt
        );

        // when
        Payment roundTripped = mapper.toDomain(mapper.toEntity(original));

        // then
        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getOrderId()).isEqualTo(original.getOrderId());
        assertThat(roundTripped.getAmount()).isEqualByComparingTo(original.getAmount());
        assertThat(roundTripped.getStatus()).isEqualTo(original.getStatus());
        assertThat(roundTripped.getExternalTransactionId()).isEqualTo(original.getExternalTransactionId());
        assertThat(roundTripped.getRedirectUrl()).isEqualTo(original.getRedirectUrl());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
    }

    // --- helpers ---

    private Payment buildPayment(PaymentStatus status) {
        return new Payment(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"),
                status,
                "ext-" + UUID.randomUUID(),
                "https://gateway.example.com/pay",
                Instant.now()
        );
    }

    private PaymentEntity buildEntity(String status) {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .status(status)
                .externalTransactionId("ext-" + UUID.randomUUID())
                .redirectUrl("https://gateway.example.com/pay")
                .createdAt(Instant.now())
                .build();
    }
}

