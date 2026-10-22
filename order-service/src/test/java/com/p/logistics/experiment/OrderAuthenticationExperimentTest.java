package com.p.logistics.experiment;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Experiment J (Step 06 security foundation, item 2: authentication).
 * Proves SecurityConfig's HTTP Basic requirement actually enforces the
 * boundary it claims to, against the real, already-running order-service -
 * not by reading the config, but by hitting it with no credentials, the
 * wrong credentials, and the right ones.
 * <p>
 * Uses GET /orders/{unknown-id} for all three cases so no order/inventory
 * setup is needed: with the correct credentials the request reaches
 * OrderService.getOrder(), which throws for an unknown id (uncaught ->
 * 500) - the point isn't that lookup succeeding, it's that the response is
 * no longer 401, proving the request got past the security layer at all.
 * <p>
 * Requires {@code docker compose up}: order-service on localhost:8080.
 */
class OrderAuthenticationExperimentTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String CORRECT_CREDENTIALS = "order-client:order-client-secret";
    private static final String WRONG_CREDENTIALS = "order-client:not-the-real-password";

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void requestWithNoCredentialsIsRejected() throws Exception {
        HttpResponse<String> response = get(null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void requestWithWrongCredentialsIsRejected() throws Exception {
        HttpResponse<String> response = get(WRONG_CREDENTIALS);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void requestWithCorrectCredentialsGetsPastTheSecurityLayer() throws Exception {
        HttpResponse<String> response = get(CORRECT_CREDENTIALS);

        assertThat(response.statusCode()).isNotEqualTo(401);
    }

    @Test
    void actuatorHealthNeedsNoCredentialsAtAll() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/actuator/health")).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String credentials) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create(BASE_URL + "/orders/" + UUID.randomUUID()));
        if (credentials != null) {
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
        }
        return httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
