package com.rzodeczko.paymentservice.infrastructure.gateway.tpay.adapter;

import com.rzodeczko.paymentservice.application.port.input.NotificationCommand;
import com.rzodeczko.paymentservice.application.port.output.GatewayResult;
import com.rzodeczko.paymentservice.application.port.output.PaymentGatewayPort;
import com.rzodeczko.paymentservice.infrastructure.configuration.properties.TPayProperties;
import com.rzodeczko.paymentservice.infrastructure.gateway.tpay.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Infrastructure adapter implementing {@link PaymentGatewayPort} using the TPay REST API.
 *
 * <p>Handles OAuth2 token acquisition with an in-memory cache to minimize round-trips to
 * the authorization endpoint. Token renewal is guarded by a {@link ReentrantLock} and a
 * double-check pattern to prevent thundering-herd under high virtual-thread concurrency.</p>
 *
 *
 */
@Component
@Slf4j
public class TPayGatewayAdapter implements PaymentGatewayPort {
    private final TPayProperties tPayProperties;
    private final RestClient restClient;

    /**
     * Cached bearer token valid for the current TTL window.
     *
     * <p>Declared {@code volatile} so that the happy-path check (token still valid)
     * never requires acquiring the lock.</p>
     */
    private volatile String cachedToken;

    /**
     * Expiry instant of the currently cached token. Defaults to {@link Instant#MIN}.
     */
    private volatile Instant tokenExpiry = Instant.MIN;

    /**
     * Lock used to serialize token-refresh requests.
     *
     * <p>Without this guard, hundreds of virtual threads detecting an expired token
     * simultaneously would each fire an OAuth request to TPay. The lock ensures a
     * single refresh while other threads wait and reuse the resulting token.</p>
     */
    private final ReentrantLock tokenLock = new ReentrantLock();

    /**
     * Creates a TPay gateway adapter with a RestClient bound to the configured API base URL.
     *
     * @param tPayProperties    strongly typed TPay integration properties
     * @param restClientBuilder RestClient builder used to construct a gateway-scoped client
     */
    public TPayGatewayAdapter(TPayProperties tPayProperties, RestClient.Builder restClientBuilder) {
        this.tPayProperties = tPayProperties;
        this.restClient = restClientBuilder
                .baseUrl(tPayProperties.api().url())
                .build();
    }

    /**
     * Registers a new transaction in TPay and returns the resulting gateway redirect URL.
     *
     * @param orderId internal order identifier bound to the payment attempt
     * @param amount  transaction amount to be charged
     * @param email   payer email forwarded to the gateway
     * @param name    payer display name forwarded to the gateway
     * @return {@link GatewayResult} containing the payment redirect URL and external transaction ID
     * @throws IllegalStateException when TPay returns an empty or malformed registration response
     */
    @Override
    public GatewayResult registerTransaction(UUID orderId, BigDecimal amount, String email, String name) {
        String accessToken = fetchAccessToken();

        var request = new TPayTransactionRequestDto(
                amount,
                "PLN",
                "ORDER " + orderId,
                orderId.toString(),
                "pl",
                new PayerDto(email, name),
                new CallbacksDto(
                        new PayerUrlsDto(
                                tPayProperties.app().returnSuccessUrl(),
                                tPayProperties.app().returnErrorUrl()
                        ),
                        new NotificationDto(
                                tPayProperties.app().notificationUrl(),
                                email
                        )
                )
        );

        var response = restClient
                .post()
                .uri("/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TPayTransactionResponseDto.class);

        if (response == null || response.transactionPaymentUrl() == null) {
            throw new IllegalStateException("TPay registration failed: empty response");
        }

        // TPay returns transactionId here; it will come back as trId in webhook notifications,
        // which is used for findByExternalTransactionId(notification.trId()) lookups.
        return new GatewayResult(response.transactionPaymentUrl(), orderId.toString());
    }

    /**
     * Verifies whether a TPay transaction has reached a confirmed state by querying its status.
     *
     * <p>A TPay 5xx error is propagated as an {@link IllegalStateException} so that the caller
     * can return a 500 to TPay, causing it to retry the notification rather than silently losing
     * a confirmed payment.</p>
     *
     * @param externalTransactionId gateway-side transaction identifier (TPay {@code trId})
     * @return {@code true} when the transaction status is {@code success}, otherwise {@code false}
     * @throws IllegalStateException when the TPay API responds with a 5xx status
     */
    @Override
    public boolean verifyTransactionConfirmed(String externalTransactionId) {
        String accessToken = fetchAccessToken();
        try {
            var response = restClient
                    .get()
                    .uri("/transactions/" + externalTransactionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        // Propagate as IllegalStateException so the controller returns 500 to TPay,
                        // triggering a retry rather than silently losing the payment confirmation.
                        throw new IllegalStateException("TPay verification unavailable, HTTP " + res.getStatusCode());
                    })
                    .body(TPayTransactionResponseDto.class);

            return response != null && "correct".equalsIgnoreCase(response.status());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("TPay verification network error. transactionId={}: {}",
                    externalTransactionId, e.getMessage());
            return false;
        }
    }

    /**
     * Validates the authenticity of an incoming TPay notification using HMAC-MD5.
     *
     * <p>Expected signature: {@code MD5(merchantId + trId + trAmount + trCrc + securityCode)}.
     * The {@code trCrc} value is the {@code orderId} set during transaction registration.</p>
     *
     * @param n normalized notification payload received from TPay
     * @return {@code true} when the computed MD5 matches the signature in the notification
     */
    @Override
    public boolean verifyNotificationSignature(NotificationCommand n) {
        String dataToHash = n.merchantId() + n.trId() + n.trAmount() + n.trCrc() + tPayProperties.api().securityCode();
        String calculated = md5Hex(dataToHash);
        return calculated.equalsIgnoreCase(n.md5Sum());
    }

    private String md5Hex(String value) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    /**
     * Returns a valid OAuth2 bearer token, fetching a new one from TPay when the cached token
     * has expired.
     *
     * <p>Uses a volatile fast-path check followed by a {@link ReentrantLock} with double-check
     * to guarantee at most one concurrent token-refresh request under high concurrency.</p>
     *
     * @return valid bearer access token
     * @throws IllegalStateException when TPay returns an empty OAuth response
     */
    private String fetchAccessToken() {

        // Fast path: token is still valid — return immediately without acquiring the lock.
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        tokenLock.lock();
        try {
            // Double-check: another thread may have refreshed the token while we waited for the lock.
            if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
                return cachedToken;
            }

            log.debug("Fetching new TPay access token");

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", tPayProperties.api().clientId());
            body.add("client_secret", tPayProperties.api().clientSecret());

            var response = restClient
                    .post()
                    .uri("/oauth/auth")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(OAuthResponseDto.class);

            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException("Failed to obtain TPay access token");
            }

            this.cachedToken = response.accessToken();
            // Use 3500s instead of 3600s to build in a 100s safety buffer before expiry.
            this.tokenExpiry = Instant.now().plusSeconds(3500);
            return this.cachedToken;
        } finally {
            tokenLock.unlock();
        }
    }
}
