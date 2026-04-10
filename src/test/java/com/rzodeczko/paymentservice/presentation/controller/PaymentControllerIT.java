package com.rzodeczko.paymentservice.presentation.controller;

import com.rzodeczko.paymentservice.PaymentServiceApplication;
import com.rzodeczko.paymentservice.application.port.output.GatewayResult;
import com.rzodeczko.paymentservice.application.port.output.PaymentGatewayPort;
import com.rzodeczko.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import com.rzodeczko.paymentservice.infrastructure.persistence.repository.JpaOutboxEventRepository;
import com.rzodeczko.paymentservice.infrastructure.persistence.repository.JpaPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = PaymentServiceApplication.class
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PaymentControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JpaPaymentRepository jpaPaymentRepository;

    @Autowired
    private JpaOutboxEventRepository jpaOutboxEventRepository;

    @MockitoBean
    private PaymentGatewayPort paymentGatewayPort;

    @BeforeEach
    void setUp() {
        jpaOutboxEventRepository.deleteAll();
        jpaPaymentRepository.deleteAll();
    }

    // ─── POST /payments/init ────────────────────────────────────────────────────

    @Test
    void initPayment_shouldPersistPaymentAndReturnPaymentIdAndRedirectUrl() throws Exception {
        UUID orderId = UUID.randomUUID();
        String externalTxId = orderId.toString();
        String redirectUrl = "https://pay.tpay.test/redirect";

        when(paymentGatewayPort.registerTransaction(
                eq(orderId), eq(new BigDecimal("99.99")),
                eq("john.doe@example.com"), eq("John Doe")))
                .thenReturn(new GatewayResult(redirectUrl, externalTxId));

        String payload = """
                {
                  "orderId": "%s",
                  "amount": 99.99,
                  "email": "john.doe@example.com",
                  "name": "John Doe"
                }
                """.formatted(orderId);

        mockMvc.perform(post("/payments/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").isNotEmpty())
                .andExpect(jsonPath("$.redirectUrl").value(redirectUrl));

        assertThat(jpaPaymentRepository.findAll()).hasSize(1);
        PaymentEntity saved = jpaPaymentRepository.findAll().getFirst();
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getExternalTransactionId()).isEqualTo(externalTxId);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void initPayment_shouldReturnExistingPaymentWithoutCallingGateway_whenOrderAlreadyExists() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID existingPaymentId = UUID.randomUUID();
        String redirectUrl = "https://existing.redirect.url";

        jpaPaymentRepository.save(PaymentEntity.builder()
                .id(existingPaymentId)
                .orderId(orderId)
                .externalTransactionId("ext-" + orderId)
                .amount(new BigDecimal("99.99"))
                .status("PENDING")
                .redirectUrl(redirectUrl)
                .createdAt(Instant.now())
                .build());

        String payload = """
                {
                  "orderId": "%s",
                  "amount": 99.99,
                  "email": "john.doe@example.com",
                  "name": "John Doe"
                }
                """.formatted(orderId);

        mockMvc.perform(post("/payments/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(existingPaymentId.toString()))
                .andExpect(jsonPath("$.redirectUrl").value(redirectUrl));

        verify(paymentGatewayPort, never()).registerTransaction(any(), any(), any(), any());
    }

    @Test
    void initPayment_shouldReturn400_whenPayloadIsInvalid() throws Exception {
        String payload = """
                {
                  "orderId": null,
                  "amount": 0,
                  "email": "not-an-email",
                  "name": ""
                }
                """;

        mockMvc.perform(post("/payments/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    // ─── POST /payments/notification ───────────────────────────────────────────

    @Test
    void handleNotification_shouldConfirmPaymentAndReturnTrue_whenSignatureValidAndStatusTrue() throws Exception {
        UUID orderId = UUID.randomUUID();
        String externalTxId = "crc-confirm-" + UUID.randomUUID();

        UUID paymentId = UUID.randomUUID();
        jpaPaymentRepository.save(PaymentEntity.builder()
                .id(paymentId)
                .orderId(orderId)
                .externalTransactionId(externalTxId)
                .amount(new BigDecimal("100.00"))
                .status("PENDING")
                .redirectUrl("https://tpay.com/redirect")
                .createdAt(Instant.now())
                .build());

        when(paymentGatewayPort.verifyNotificationSignature(any())).thenReturn(true);
        when(paymentGatewayPort.verifyTransactionConfirmed(any())).thenReturn(true);

        mockMvc.perform(post("/payments/notification")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("id", "merchant-1")
                        .param("tr_id", "tr-123")
                        .param("tr_date", "2026-04-07 12:00:00")
                        .param("tr_crc", externalTxId)
                        .param("tr_amount", "100.00")
                        .param("tr_paid", "100.00")
                        .param("tr_status", "TRUE")
                        .param("tr_email", "john.doe@example.com")
                        .param("tr_error", "none")
                        .param("tr_desc", "order 100")
                        .param("md5sum", "irrelevant-mocked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("TRUE"));

        PaymentEntity updated = jpaPaymentRepository.findById(paymentId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("PAID");
        assertThat(jpaOutboxEventRepository.findAll()).hasSize(1);
    }

    @Test
    void handleNotification_shouldFailPaymentAndReturnTrue_whenSignatureValidAndStatusFalse() throws Exception {
        UUID orderId = UUID.randomUUID();
        String externalTxId = "crc-fail-" + UUID.randomUUID();

        UUID paymentId = UUID.randomUUID();
        jpaPaymentRepository.save(PaymentEntity.builder()
                .id(paymentId)
                .orderId(orderId)
                .externalTransactionId(externalTxId)
                .amount(new BigDecimal("100.00"))
                .status("PENDING")
                .redirectUrl("https://tpay.com/redirect")
                .createdAt(Instant.now())
                .build());

        when(paymentGatewayPort.verifyNotificationSignature(any())).thenReturn(true);

        mockMvc.perform(post("/payments/notification")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("id", "merchant-1")
                        .param("tr_id", "tr-456")
                        .param("tr_date", "2026-04-07 12:00:00")
                        .param("tr_crc", externalTxId)
                        .param("tr_amount", "100.00")
                        .param("tr_paid", "0.00")
                        .param("tr_status", "FALSE")
                        .param("tr_email", "john.doe@example.com")
                        .param("tr_error", "declined")
                        .param("tr_desc", "order 100")
                        .param("md5sum", "irrelevant-mocked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("TRUE"));

        PaymentEntity updated = jpaPaymentRepository.findById(paymentId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void handleNotification_shouldReturn400False_whenSignatureIsInvalid() throws Exception {
        when(paymentGatewayPort.verifyNotificationSignature(any())).thenReturn(false);

        mockMvc.perform(post("/payments/notification")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("id", "merchant-1")
                        .param("tr_id", "tr-789")
                        .param("tr_date", "2026-04-07 12:00:00")
                        .param("tr_crc", "crc-invalid")
                        .param("tr_amount", "100.00")
                        .param("tr_paid", "100.00")
                        .param("tr_status", "TRUE")
                        .param("tr_email", "john.doe@example.com")
                        .param("tr_error", "none")
                        .param("tr_desc", "order 100")
                        .param("md5sum", "bad-md5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("FALSE"));
    }

    @Test
    void handleNotification_shouldReturnTrue_whenPaymentAlreadyPaid() throws Exception {
        UUID orderId = UUID.randomUUID();
        String externalTxId = "crc-paid-" + UUID.randomUUID();

        UUID paymentId = UUID.randomUUID();
        jpaPaymentRepository.save(PaymentEntity.builder()
                .id(paymentId)
                .orderId(orderId)
                .externalTransactionId(externalTxId)
                .amount(new BigDecimal("100.00"))
                .status("PAID")
                .redirectUrl("https://tpay.com/redirect")
                .createdAt(Instant.now())
                .build());

        when(paymentGatewayPort.verifyNotificationSignature(any())).thenReturn(true);

        mockMvc.perform(post("/payments/notification")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("id", "merchant-1")
                        .param("tr_id", "tr-999")
                        .param("tr_date", "2026-04-07 12:00:00")
                        .param("tr_crc", externalTxId)
                        .param("tr_amount", "100.00")
                        .param("tr_paid", "100.00")
                        .param("tr_status", "TRUE")
                        .param("tr_email", "john.doe@example.com")
                        .param("tr_error", "none")
                        .param("tr_desc", "order 100")
                        .param("md5sum", "irrelevant-mocked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("TRUE"));

        verify(paymentGatewayPort, never()).verifyTransactionConfirmed(any());
        assertThat(jpaOutboxEventRepository.findAll()).isEmpty();
    }

    // ─── GET /payments/success & /payments/error ───────────────────────────────

    @Test
    void success_shouldReturn200WithPaymentOkMessage() throws Exception {
        mockMvc.perform(get("/payments/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("PAYMENT OK"));
    }

    @Test
    void error_shouldReturn200WithPaymentErrorMessage() throws Exception {
        mockMvc.perform(get("/payments/error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("PAYMENT ERROR"));
    }
}
