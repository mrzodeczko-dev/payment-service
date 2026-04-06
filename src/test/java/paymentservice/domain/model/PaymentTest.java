package paymentservice.domain.model;

import com.rzodeczko.paymentservice.domain.model.Payment;
import com.rzodeczko.paymentservice.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PaymentTest {

    @Test
    void testCreatePayment() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("99.99");
        String externalTransactionId = "tr_12345";
        String redirectUrl = "https://example.com/redirect";

        // Act
        Payment payment = Payment.create(orderId, amount, externalTransactionId, redirectUrl);

        // Assert
        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getAmount()).isEqualByComparingTo(amount);
        assertThat(payment.getExternalTransactionId()).isEqualTo(externalTransactionId);
        assertThat(payment.getRedirectUrl()).isEqualTo(redirectUrl);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getCreatedAt()).isNotNull();
        assertThat(payment.isPaid()).isFalse();
    }


    @Test
    void testCreatePayment_ThrowsException_WhenOrderIdIsNull() {
        // Arrange
        BigDecimal amount = new BigDecimal("99.99");

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Payment.create(null, amount, "tr_123", "https://example.com"))
                .withMessage("Oder id cannot be null");
    }

    @Test
    void testCreatePayment_ThrowsException_WhenAmountIsNull() {
        // Arrange
        UUID orderId = UUID.randomUUID();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Payment.create(orderId, null, "tr_123", "https://example.com"))
                .withMessage("Amount must be positive");
    }

    @Test
    void testCreatePayment_ThrowsException_WhenAmountIsZero() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        BigDecimal zeroAmount = BigDecimal.ZERO;

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Payment.create(orderId, zeroAmount, "tr_123", "https://example.com"))
                .withMessage("Amount must be positive");
    }

    @Test
    void testCreatePayment_ThrowsException_WhenAmountIsNegative() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        BigDecimal negativeAmount = new BigDecimal("-10.00");

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Payment.create(orderId, negativeAmount, "tr_123", "https://example.com"))
                .withMessage("Amount must be positive");
    }

    @Test
    void testConfirmPayment() {
        // Arrange
        Payment payment = Payment.create(
                UUID.randomUUID(),
                new BigDecimal("99.99"),
                "tr_123",
                "https://example.com"
        );

        // Act
        payment.confirm();

        // Assert
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.isPaid()).isTrue();
    }

    @Test
    void testConfirmPayment_ThrowsException_WhenNotPending() {
        // Arrange
        Payment payment = Payment.create(
                UUID.randomUUID(),
                new BigDecimal("99.99"),
                "tr_123",
                "https://example.com"
        );
        payment.confirm();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(payment::confirm)
                .withMessageContaining("Cannot confirm payment in status")
                .withMessageContaining("Expected: Pending");
    }

    @Test
    void testFailPayment() {
        // Arrange
        Payment payment = Payment.create(
                UUID.randomUUID(),
                new BigDecimal("99.99"),
                "tr_123",
                "https://example.com"
        );

        // Act
        payment.fail();

        // Assert
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.isPaid()).isFalse();
    }

    @Test
    void testFailPayment_ThrowsException_WhenNotPending() {
        // Arrange
        Payment payment = Payment.create(
                UUID.randomUUID(),
                new BigDecimal("99.99"),
                "tr_123",
                "https://example.com"
        );
        payment.fail();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(payment::fail)
                .withMessageContaining("Cannot fail payment in status")
                .withMessageContaining("Expected: Pending");
    }

    @Test
    void testConstructorDirectly() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("99.99");
        String externalTransactionId = "tr_123";
        String redirectUrl = "https://example.com";
        Instant createdAt = Instant.now();
        PaymentStatus status = PaymentStatus.PENDING;

        // Act
        Payment payment = new Payment(id, orderId, amount, status, externalTransactionId, redirectUrl, createdAt);

        // Assert
        assertThat(payment.getId()).isEqualTo(id);
        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getAmount()).isEqualByComparingTo(amount);
        assertThat(payment.getExternalTransactionId()).isEqualTo(externalTransactionId);
        assertThat(payment.getRedirectUrl()).isEqualTo(redirectUrl);
        assertThat(payment.getCreatedAt()).isEqualTo(createdAt);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void testIsPaid_WhenPaid() {
        // Arrange
        Payment payment = Payment.create(
                UUID.randomUUID(),
                new BigDecimal("99.99"),
                "tr_123",
                "https://example.com"
        );
        payment.confirm();

        // Act & Assert
        assertThat(payment.isPaid()).isTrue();
    }

    @Test
    void testIsPaid_WhenPending() {
        // Arrange
        Payment payment = Payment.create(
                UUID.randomUUID(),
                new BigDecimal("99.99"),
                "tr_123",
                "https://example.com"
        );

        // Act & Assert
        assertThat(payment.isPaid()).isFalse();
    }

    @Test
    void testIsPaid_WhenFailed() {
        // Arrange
        Payment payment = Payment.create(
                UUID.randomUUID(),
                new BigDecimal("99.99"),
                "tr_123",
                "https://example.com"
        );
        payment.fail();

        // Act & Assert
        assertThat(payment.isPaid()).isFalse();
    }
}