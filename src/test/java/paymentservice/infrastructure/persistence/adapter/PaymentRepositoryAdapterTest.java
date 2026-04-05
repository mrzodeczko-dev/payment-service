package paymentservice.infrastructure.persistence.adapter;

import com.rzodeczko.paymentservice.domain.exception.PaymentAlreadyExistsException;
import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.model.PaymentStatus;
import com.rzodeczko.paymentservice.infrastructure.persistence.adapter.PaymentRepositoryAdapter;
import com.rzodeczko.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import com.rzodeczko.paymentservice.infrastructure.persistence.mapper.PaymentMapper;
import com.rzodeczko.paymentservice.infrastructure.persistence.repository.JpaPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRepositoryAdapterTest {

    @Mock
    private JpaPaymentRepository jpaPaymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentRepositoryAdapter repositoryAdapter;

    @Test
    void save_shouldUpdateExistingEntityStatus_whenPaymentAlreadyExists() {
        // given
        Payment payment = buildPayment(PaymentStatus.PAID);
        PaymentEntity existingEntity = buildEntity(PaymentStatus.PENDING.name());
        existingEntity.setId(payment.getId());

        Payment mappedDomain = buildPayment(PaymentStatus.PAID);

        when(jpaPaymentRepository.findById(payment.getId())).thenReturn(Optional.of(existingEntity));
        when(jpaPaymentRepository.saveAndFlush(existingEntity)).thenReturn(existingEntity);
        when(paymentMapper.toDomain(existingEntity)).thenReturn(mappedDomain);

        // when
        Payment result = repositoryAdapter.save(payment);

        // then
        assertThat(existingEntity.getStatus()).isEqualTo(PaymentStatus.PAID.name());
        assertThat(result).isSameAs(mappedDomain);

        verify(jpaPaymentRepository).findById(payment.getId());
        verify(jpaPaymentRepository).saveAndFlush(existingEntity);
        verify(paymentMapper, never()).toEntity(payment);
        verify(paymentMapper).toDomain(existingEntity);
    }

    @Test
    void save_shouldCreateNewEntity_whenPaymentDoesNotExist() {
        // given
        Payment payment = buildPayment(PaymentStatus.PENDING);
        PaymentEntity mappedEntity = buildEntity(PaymentStatus.PENDING.name());
        mappedEntity.setId(payment.getId());
        mappedEntity.setOrderId(payment.getOrderId());
        mappedEntity.setAmount(payment.getAmount());
        mappedEntity.setExternalTransactionId(payment.getExternalTransactionId());
        mappedEntity.setRedirectUrl(payment.getRedirectUrl());
        mappedEntity.setCreatedAt(payment.getCreatedAt());

        Payment mappedDomain = buildPayment(PaymentStatus.PENDING);

        when(jpaPaymentRepository.findById(payment.getId())).thenReturn(Optional.empty());
        when(paymentMapper.toEntity(payment)).thenReturn(mappedEntity);
        when(jpaPaymentRepository.saveAndFlush(mappedEntity)).thenReturn(mappedEntity);
        when(paymentMapper.toDomain(mappedEntity)).thenReturn(mappedDomain);

        // when
        Payment result = repositoryAdapter.save(payment);

        // then
        assertThat(result).isSameAs(mappedDomain);

        verify(jpaPaymentRepository).findById(payment.getId());
        verify(paymentMapper).toEntity(payment);
        verify(jpaPaymentRepository).saveAndFlush(mappedEntity);
        verify(paymentMapper).toDomain(mappedEntity);
    }

    @Test
    void save_shouldThrowPaymentAlreadyExistsException_whenConstraintViolationOccurs() {
        // given
        Payment payment = buildPayment(PaymentStatus.PENDING);
        PaymentEntity mappedEntity = buildEntity(PaymentStatus.PENDING.name());

        when(jpaPaymentRepository.findById(payment.getId())).thenReturn(Optional.empty());
        when(paymentMapper.toEntity(payment)).thenReturn(mappedEntity);
        when(jpaPaymentRepository.saveAndFlush(mappedEntity))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        // when / then
        assertThatThrownBy(() -> repositoryAdapter.save(payment))
                .isInstanceOf(PaymentAlreadyExistsException.class)
                .hasMessageContaining(payment.getOrderId().toString());
    }

    @Test
    void findByExternalTransactionId_shouldReturnMappedPayment_whenEntityExists() {
        // given
        String externalTransactionId = "ext-123";
        PaymentEntity entity = buildEntity(PaymentStatus.PAID.name());
        Payment mappedDomain = buildPayment(PaymentStatus.PAID);

        when(jpaPaymentRepository.findByExternalTransactionId(externalTransactionId)).thenReturn(Optional.of(entity));
        when(paymentMapper.toDomain(entity)).thenReturn(mappedDomain);

        // when
        Optional<Payment> result = repositoryAdapter.findByExternalTransactionId(externalTransactionId);

        // then
        assertThat(result).containsSame(mappedDomain);
        verify(jpaPaymentRepository).findByExternalTransactionId(externalTransactionId);
        verify(paymentMapper).toDomain(entity);
    }

    @Test
    void findByExternalTransactionId_shouldReturnEmpty_whenEntityDoesNotExist() {
        // given
        String externalTransactionId = "ext-missing";
        when(jpaPaymentRepository.findByExternalTransactionId(externalTransactionId)).thenReturn(Optional.empty());

        // when
        Optional<Payment> result = repositoryAdapter.findByExternalTransactionId(externalTransactionId);

        // then
        assertThat(result).isEmpty();
        verify(jpaPaymentRepository).findByExternalTransactionId(externalTransactionId);
        verify(paymentMapper, never()).toDomain(org.mockito.ArgumentMatchers.any(PaymentEntity.class));
    }

    @Test
    void findByOrderId_shouldReturnMappedPayment_whenEntityExists() {
        // given
        UUID orderId = UUID.randomUUID();
        PaymentEntity entity = buildEntity(PaymentStatus.PAID.name());
        Payment mappedDomain = buildPayment(PaymentStatus.PAID);

        when(jpaPaymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(entity));
        when(paymentMapper.toDomain(entity)).thenReturn(mappedDomain);

        // when
        Optional<Payment> result = repositoryAdapter.findByOrderId(orderId);

        // then
        assertThat(result).containsSame(mappedDomain);
        verify(jpaPaymentRepository).findByOrderId(orderId);
        verify(paymentMapper).toDomain(entity);
    }

    @Test
    void findByOrderId_shouldReturnEmpty_whenEntityDoesNotExist() {
        // given
        UUID orderId = UUID.randomUUID();
        when(jpaPaymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        // when
        Optional<Payment> result = repositoryAdapter.findByOrderId(orderId);

        // then
        assertThat(result).isEmpty();
        verify(jpaPaymentRepository).findByOrderId(orderId);
        verify(paymentMapper, never()).toDomain(org.mockito.ArgumentMatchers.any(PaymentEntity.class));
    }

    private Payment buildPayment(PaymentStatus status) {
        return new Payment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("129.99"),
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
                .externalTransactionId("ext-" + UUID.randomUUID())
                .amount(new BigDecimal("129.99"))
                .status(status)
                .redirectUrl("https://gateway.example.com/pay")
                .createdAt(Instant.now())
                .build();
    }
}

