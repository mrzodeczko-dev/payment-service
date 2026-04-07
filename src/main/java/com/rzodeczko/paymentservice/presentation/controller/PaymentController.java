package com.rzodeczko.paymentservice.presentation.controller;


import com.rzodeczko.paymentservice.application.port.input.InitPaymentResult;
import com.rzodeczko.paymentservice.application.port.input.NotificationCommand;
import com.rzodeczko.paymentservice.application.port.input.PaymentUseCase;
import com.rzodeczko.paymentservice.domain.exception.PaymentConcurrentModificationException;
import com.rzodeczko.paymentservice.presentation.dto.InitPaymentRequestDto;
import com.rzodeczko.paymentservice.presentation.dto.InitPaymentResponseDto;
import com.rzodeczko.paymentservice.presentation.dto.NotificationResponseDto;
import com.rzodeczko.paymentservice.presentation.dto.PaymentResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing payment-related endpoints.
 *
 * <p>Provides operations to initialize payments, process TPay notifications,
 * and handle success/error callbacks.</p>
 */
@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentUseCase paymentUseCase;

    /**
     * Initializes a payment for the provided order and customer data.
     *
     * @param request request payload with order and payer details
     * @return HTTP 200 with payment identifier and redirect URL
     */
    @PostMapping("/init")
    public ResponseEntity<InitPaymentResponseDto> initPayment(@Valid @RequestBody InitPaymentRequestDto request) {
        InitPaymentResult result = paymentUseCase
                .initPayment(
                        request.orderId(),
                        request.amount(),
                        request.email(),
                        request.name()
                );
        return ResponseEntity.ok(new InitPaymentResponseDto(result.paymentId(), result.redirectUrl()));
    }

    /**
     * Handles asynchronous TPay webhook notifications.
     *
     * @param merchantId TPay merchant identifier ({@code id})
     * @param trId       TPay transaction identifier ({@code tr_id})
     * @param trDate     transaction date returned by TPay ({@code tr_date})
     * @param trCrc      correlation value returned by TPay ({@code tr_crc})
     * @param trAmount   transaction amount ({@code tr_amount})
     * @param trPaid     amount paid ({@code tr_paid})
     * @param trStatus   payment status ({@code tr_status})
     * @param trEmail    customer email ({@code tr_email})
     * @param trError    TPay error code ({@code tr_error})
     * @param trDesc     transaction description ({@code tr_desc})
     * @param md5Sum     webhook signature ({@code md5sum})
     * @return HTTP 200/400/500 with {@link NotificationResponseDto}
     */
    @PostMapping(value = "/notification", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<NotificationResponseDto> handleNotification(
            @RequestParam("id") String merchantId,
            @RequestParam("tr_id") String trId,
            @RequestParam("tr_date") String trDate,
            @RequestParam("tr_crc") String trCrc,
            @RequestParam("tr_amount") String trAmount,
            @RequestParam("tr_paid") String trPaid,
            @RequestParam("tr_status") String trStatus,
            @RequestParam("tr_email") String trEmail,
            @RequestParam("tr_error") String trError,
            @RequestParam("tr_desc") String trDesc,
            @RequestParam("md5sum") String md5Sum
    ) {
        try {
            NotificationCommand command = new NotificationCommand(
                    merchantId, trId, trDate, trCrc, trAmount, trPaid, trDesc, trStatus, trError, trEmail, md5Sum);
            paymentUseCase.handleNotification(command);
            return ResponseEntity.ok(new NotificationResponseDto("TRUE"));
        } catch (PaymentConcurrentModificationException e) {
            log.warn("Concurrent notification, returning 500 for TPay retry. trId={}", trId);
            return ResponseEntity.internalServerError().body(new NotificationResponseDto("FALSE"));
        } catch (IllegalStateException e) {
            log.error("Notification processing failed. trId={}, reason={}", trId, e.getMessage());
            return ResponseEntity.internalServerError().body(new NotificationResponseDto("FALSE"));
        } catch (Exception e) {
            log.error("Notification rejected. trId={}, reason={}", trId, e.getMessage());
            return ResponseEntity.badRequest().body(new NotificationResponseDto("FALSE"));
        }
    }

    /**
     * Returns success callback response after a completed payment flow.
     *
     * @return HTTP 200 with success message
     */
    @GetMapping("/success")
    public ResponseEntity<PaymentResponseDto> success() {
        return ResponseEntity.ok(new PaymentResponseDto("PAYMENT OK"));
    }

    /**
     * Returns error callback response after a failed payment flow.
     *
     * @return HTTP 200 with error message
     */
    @GetMapping("/error")
    public ResponseEntity<PaymentResponseDto> error() {
        return ResponseEntity.ok(new PaymentResponseDto("PAYMENT ERROR"));
    }
}
