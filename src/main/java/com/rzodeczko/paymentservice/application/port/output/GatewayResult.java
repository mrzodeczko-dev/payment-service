package com.rzodeczko.paymentservice.application.port.output;

/**
 * Immutable value object returned by the payment gateway adapter after
 * successful transaction registration.
 *
 * @param redirectUrl gateway-hosted URL where the customer is redirected to
 *                    continue authorization or payment confirmation
 * @param externalTransactionId provider-side transaction identifier used for
 *                              status verification and reconciliation
 */
public record GatewayResult(String redirectUrl, String externalTransactionId) {
}
