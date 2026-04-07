package com.rzodeczko.paymentservice.infrastructure.gateway.tpay.dto;

import java.math.BigDecimal;

/**
 * DTO representing the transaction creation request sent to the TPay API.
 *
 * @param amount transaction amount expected by the payment gateway
 * @param currency ISO currency code used for transaction settlement
 * @param description merchant-side transaction description visible in gateway context
 * @param lang language code used for payer-facing gateway screens
 * @param payer payer identity details associated with the transaction
 * @param callbacks callback configuration for payer redirects and asynchronous notifications
 */
public record TPayTransactionRequestDto(
        BigDecimal amount,
        String currency,
        String description,
        String hiddenDescription,
        String lang,
        PayerDto payer,
        CallbacksDto callbacks
) {
}
