package com.rzodeczko.paymentservice.presentation.controller;

import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.application.port.input.NotificationCommand;
import com.rzodeczko.paymentservice.application.port.input.PaymentUseCase;
import com.rzodeczko.paymentservice.domain.exception.PaymentConcurrentModificationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PaymentController.class)
@ActiveProfiles("test")
class PaymentControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentUseCase paymentUseCase;

    @Test
    void initPayment_shouldReturnPaymentIdAndRedirectUrl() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        when(paymentUseCase.initPayment(orderId, new BigDecimal("99.99"), "john.doe@example.com", "John Doe"))
                .thenReturn(new InitPaymentResult(paymentId, "https://tpay.com/redirect"));

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
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.redirectUrl").value("https://tpay.com/redirect"));

        verify(paymentUseCase).initPayment(orderId, new BigDecimal("99.99"), "john.doe@example.com", "John Doe");
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
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.message", containsString("email")));
    }

    @Test
    void handleNotification_shouldReturnTrue_whenProcessingSucceeds() throws Exception {
        performNotificationRequest()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("TRUE"));

        verify(paymentUseCase).handleNotification(argThat(command ->
                command.merchantId().equals("merchant-1")
                        && command.trId().equals("tr-123")
                        && command.trCrc().equals("crc-abc")
                        && command.trStatus().equals("TRUE")
                        && command.md5Sum().equals("md5-value")
        ));
    }

    @Test
    void handleNotification_shouldReturn500_whenConcurrentModificationOccurs() throws Exception {
        doThrow(new PaymentConcurrentModificationException())
                .when(paymentUseCase)
                .handleNotification(any(NotificationCommand.class));

        performNotificationRequest()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.result").value("FALSE"));
    }

    @Test
    void handleNotification_shouldReturn500_whenIllegalStateOccurs() throws Exception {
        doThrow(new IllegalStateException("tpay unavailable"))
                .when(paymentUseCase)
                .handleNotification(any(NotificationCommand.class));

        performNotificationRequest()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.result").value("FALSE"));
    }

    @Test
    void handleNotification_shouldReturn400_whenUnexpectedExceptionOccurs() throws Exception {
        doThrow(new RuntimeException("invalid signature"))
                .when(paymentUseCase)
                .handleNotification(any(NotificationCommand.class));

        performNotificationRequest()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("FALSE"));
    }

    @Test
    void success_shouldReturnPaymentOkMessage() throws Exception {
        mockMvc.perform(get("/payments/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("PAYMENT OK"));
    }

    @Test
    void error_shouldReturnPaymentErrorMessage() throws Exception {
        mockMvc.perform(get("/payments/error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("PAYMENT ERROR"));
    }

    private ResultActions performNotificationRequest() throws Exception {
        return mockMvc.perform(post("/payments/notification")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id", "merchant-1")
                .param("tr_id", "tr-123")
                .param("tr_date", "2026-04-07 12:00:00")
                .param("tr_crc", "crc-abc")
                .param("tr_amount", "100.00")
                .param("tr_paid", "100.00")
                .param("tr_status", "TRUE")
                .param("tr_email", "john.doe@example.com")
                .param("tr_error", "none")
                .param("tr_desc", "order 100")
                .param("md5sum", "md5-value"));
    }
}
