package com.rzodeczko.paymentservice.contract;

import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.application.port.input.PaymentUseCase;
import com.rzodeczko.paymentservice.presentation.controller.PaymentController;
import com.rzodeczko.paymentservice.presentation.exception.GlobalExceptionHandler;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class BaseContractTest {

    private final PaymentUseCase paymentUseCase = mock(PaymentUseCase.class);

    @BeforeEach
    void setup() {
        when(paymentUseCase.initPayment(any(UUID.class), any(), anyString(), anyString()))
                .thenReturn(new InitPaymentResult(
                        UUID.randomUUID(),
                        "https://payment-gateway.example.com/pay/abc123"
                ));

        doNothing().when(paymentUseCase).refundPayment(any(UUID.class));

        RestAssuredMockMvc.standaloneSetup(
                new PaymentController(paymentUseCase),
                new GlobalExceptionHandler()
        );
    }
}
