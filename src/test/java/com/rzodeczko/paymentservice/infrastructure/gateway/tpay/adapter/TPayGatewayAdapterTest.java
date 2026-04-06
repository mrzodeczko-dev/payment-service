package com.rzodeczko.paymentservice.infrastructure.gateway.tpay.adapter;

import com.rzodeczko.paymentservice.application.port.input.NotificationCommand;
import com.rzodeczko.paymentservice.application.port.output.GatewayResult;
import com.rzodeczko.paymentservice.infrastructure.configuration.properties.TPayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TPayGatewayAdapterTest {

    private static final String BASE_URL = "https://api.tpay.test";

    @Test
    void registerTransaction_shouldReturnGatewayResult_whenGatewayRespondsWithValidPayload() {
        // given
        TestContext context = createContext();
        UUID orderId = UUID.randomUUID();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/transactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess(
                        "{\"transactionPaymentUrl\":\"https://pay.tpay.test/redirect\",\"transactionId\":\"tx-123\",\"status\":\"created\"}",
                        MediaType.APPLICATION_JSON));

        // when
        GatewayResult result = context.adapter.registerTransaction(orderId, new BigDecimal("120.50"), "john@doe.com", "John Doe");

        // then
        assertThat(result.redirectUrl()).isEqualTo("https://pay.tpay.test/redirect");
        assertThat(result.externalTransactionId()).isEqualTo("tx-123");
        context.server.verify();
    }

    @Test
    void registerTransaction_shouldThrowIllegalStateException_whenGatewayResponseIsMissingPaymentUrl() {
        // given
        TestContext context = createContext();
        UUID orderId = UUID.randomUUID();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/transactions"))
                .andRespond(withSuccess("{\"transactionId\":\"tx-123\",\"status\":\"created\"}", MediaType.APPLICATION_JSON));

        // when / then
        assertThatThrownBy(() -> context.adapter.registerTransaction(orderId, new BigDecimal("12.00"), "john@doe.com", "John Doe"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TPay registration failed");

        context.server.verify();
    }

    @Test
    void registerTransaction_shouldThrowIllegalStateException_whenGatewayResponseIsNull() {
        // given
        TestContext context = createContext();
        UUID orderId = UUID.randomUUID();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/transactions"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        // when / then
        assertThatThrownBy(() -> context.adapter.registerTransaction(orderId, new BigDecimal("12.00"), "john@doe.com", "John Doe"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TPay registration failed");

        context.server.verify();
    }

    @Test
    void verifyTransactionConfirmed_shouldReturnTrue_whenGatewayStatusIsSuccess() {
        // given
        TestContext context = createContext();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/transactions/tx-100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

        // when
        boolean confirmed = context.adapter.verifyTransactionConfirmed("tx-100");

        // then
        assertThat(confirmed).isTrue();
        context.server.verify();
    }

    @Test
    void verifyTransactionConfirmed_shouldReturnFalse_whenGatewayStatusIsNotSuccess() {
        // given
        TestContext context = createContext();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/transactions/tx-101"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"pending\"}", MediaType.APPLICATION_JSON));

        // when
        boolean confirmed = context.adapter.verifyTransactionConfirmed("tx-101");

        // then
        assertThat(confirmed).isFalse();
        context.server.verify();
    }

    @Test
    void verifyTransactionConfirmed_shouldReturnFalse_whenGatewayCallFailsWithNetworkException() {
        // given
        TestContext context = createContext();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/transactions/tx-200"))
                .andRespond(request -> {
                    throw new IOException("Connection reset");
                });

        // when
        boolean confirmed = context.adapter.verifyTransactionConfirmed("tx-200");

        // then
        assertThat(confirmed).isFalse();
        context.server.verify();
    }

    @Test
    void verifyTransactionConfirmed_shouldThrowIllegalStateException_whenGatewayReturns5xx() {
        // given
        TestContext context = createContext();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/transactions/tx-500"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // when / then
        assertThatThrownBy(() -> context.adapter.verifyTransactionConfirmed("tx-500"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TPay verification unavailable");

        context.server.verify();
    }

    @Test
    void verifyTransactionConfirmed_shouldReuseCachedToken_whenSecondCallIsWithinTokenTtl() {
        // given
        TestContext context = createContext();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("{\"access_token\":\"token-cached\"}", MediaType.APPLICATION_JSON));

        context.server.expect(ExpectedCount.times(2), requestTo(BASE_URL + "/transactions/tx-cached"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-cached"))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

        // when
        boolean first = context.adapter.verifyTransactionConfirmed("tx-cached");
        boolean second = context.adapter.verifyTransactionConfirmed("tx-cached");

        // then
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        context.server.verify();
    }

    @Test
    void verifyTransactionConfirmed_shouldRefreshToken_whenCachedTokenExpired() throws Exception {
        // given
        TestContext context = createContext();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/transactions/tx-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("{\"access_token\":\"token-2\"}", MediaType.APPLICATION_JSON));
        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/transactions/tx-2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-2"))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

        // when
        boolean first = context.adapter.verifyTransactionConfirmed("tx-1");
        setField(context.adapter, "tokenExpiry", Instant.now().minusSeconds(1));
        boolean second = context.adapter.verifyTransactionConfirmed("tx-2");

        // then
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        context.server.verify();
    }

    @Test
    void verifyTransactionConfirmed_shouldThrowIllegalStateException_whenOAuthResponseHasNoToken() {
        // given
        TestContext context = createContext();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when / then
        assertThatThrownBy(() -> context.adapter.verifyTransactionConfirmed("tx-no-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to obtain TPay access token");

        context.server.verify();
    }

    @Test
    void verifyTransactionConfirmed_shouldThrowIllegalStateException_whenOAuthResponseIsNull() {
        // given
        TestContext context = createContext();

        context.server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/oauth/auth"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        // when / then
        assertThatThrownBy(() -> context.adapter.verifyTransactionConfirmed("tx-null-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to obtain TPay access token");

        context.server.verify();
    }

    @Test
    void verifyNotificationSignature_shouldReturnTrue_whenMd5MatchesIgnoringCase() {
        // given
        TPayProperties properties = tPayProperties();
        TPayGatewayAdapter adapter = new TPayGatewayAdapter(properties, RestClient.builder());

        String payloadToHash = "merchant-1" + "tr-1" + "10.00" + "crc-1" + properties.api().securityCode();
        String expectedMd5Uppercase = md5HexUppercase(payloadToHash);

        NotificationCommand command = new NotificationCommand(
                "merchant-1",
                "tr-1",
                "2026-04-05T12:00:00Z",
                "crc-1",
                "10.00",
                "10.00",
                "ORDER",
                "TRUE",
                "",
                "john@doe.com",
                expectedMd5Uppercase
        );

        // when
        boolean valid = adapter.verifyNotificationSignature(command);

        // then
        assertThat(valid).isTrue();
    }

    @Test
    void verifyNotificationSignature_shouldReturnFalse_whenMd5DoesNotMatch() {
        // given
        TPayProperties properties = tPayProperties();
        TPayGatewayAdapter adapter = new TPayGatewayAdapter(properties, RestClient.builder());

        NotificationCommand command = new NotificationCommand(
                "merchant-1",
                "tr-1",
                "2026-04-05T12:00:00Z",
                "crc-1",
                "10.00",
                "10.00",
                "ORDER",
                "TRUE",
                "",
                "john@doe.com",
                "not-a-valid-md5"
        );

        // when
        boolean valid = adapter.verifyNotificationSignature(command);

        // then
        assertThat(valid).isFalse();
    }

    private TestContext createContext() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TPayGatewayAdapter adapter = new TPayGatewayAdapter(tPayProperties(), builder);
        return new TestContext(adapter, server);
    }

    private TPayProperties tPayProperties() {
        return new TPayProperties(
                new TPayProperties.Api(BASE_URL, "client-id", "client-secret", "sec-123"),
                new TPayProperties.App("https://app.test/notify", "https://app.test/success", "https://app.test/error")
        );
    }

    private String md5HexUppercase(String value) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record TestContext(TPayGatewayAdapter adapter, MockRestServiceServer server) {
    }
}
