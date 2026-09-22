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
 * Experiment K (Step 06 security foundation, item 3: authorization).
 * Proves SecurityConfig's role restriction on the two operator-only
 * shipment routes is enforced against the real, already-running
 * order-service - a CUSTOMER-role caller is authenticated correctly yet
 * still turned away, while an OPERATOR-role caller making the identical
 * request is let through the security layer.
 * <p>
 * Uses a random order id for the routes under test, same approach as
 * OrderAuthenticationExperimentTest: the point isn't that the business
 * operation succeeds (a random id makes the downstream service fail),
 * it's that the response is no longer 403, proving authorization - not
 * just authentication - let the request through.
 * <p>
 * Requires {@code docker compose up}: order-service on localhost:8080.
 */
class OrderAuthorizationExperimentTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String CUSTOMER_CREDENTIALS = "order-client:order-client-secret";
    private static final String OPERATOR_CREDENTIALS = "operator:operator-secret";

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void customerCannotManuallyCreateAShipment() throws Exception {
        HttpResponse<String> response = post("/shipments", createShipmentBody(), CUSTOMER_CREDENTIALS);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void operatorCanManuallyCreateAShipment() throws Exception {
        HttpResponse<String> response = post("/shipments", createShipmentBody(), OPERATOR_CREDENTIALS);

        assertThat(response.statusCode()).isNotEqualTo(403);
    }

    @Test
    void customerCannotUpdateShipmentStatus() throws Exception {
        HttpResponse<String> response = put(
                "/shipments/" + UUID.randomUUID() + "/status?status=IN_TRANSIT", CUSTOMER_CREDENTIALS);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void operatorCanUpdateShipmentStatus() throws Exception {
        HttpResponse<String> response = put(
                "/shipments/" + UUID.randomUUID() + "/status?status=IN_TRANSIT", OPERATOR_CREDENTIALS);

        assertThat(response.statusCode()).isNotEqualTo(403);
    }

    @Test
    void customerKeepsAccessToItsOwnRoutes() throws Exception {
        HttpResponse<String> response = get("/orders/" + UUID.randomUUID(), CUSTOMER_CREDENTIALS);

        assertThat(response.statusCode()).isNotEqualTo(403);
    }

    private String createShipmentBody() {
        return """
                {
                    "orderId": "%s",
                    "destination": "Berlin"
                }
                """.formatted(UUID.randomUUID());
    }

    private HttpResponse<String> get(String path, String credentials) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Authorization", basicAuth(credentials))
                .GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody, String credentials) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .header("Authorization", basicAuth(credentials))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String credentials) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Authorization", basicAuth(credentials))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String basicAuth(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}
