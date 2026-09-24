package com.p.logistics.inventory.experiment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Experiment I (Step 06 security foundation, item 1: service identity).
 * Proves InternalApiKeyFilter actually enforces the boundary it claims to -
 * not by reading the filter's code, but by hitting the real, already-running
 * inventory-service over real HTTP with no key, the wrong key, and the
 * right key, and checking what comes back each time.
 * <p>
 * Requires {@code docker compose up}: inventory-service on localhost:8081,
 * postgres-inventory on localhost:5434.
 */
class InternalApiKeyExperimentTest {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String CORRECT_KEY = "dev-only-internal-key";
    private static final String DB_URL = "jdbc:postgresql://localhost:5434/inventory";
    private static final String DB_USER = "inventory";
    private static final String DB_PASSWORD = "inventory";

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private String productId;

    @BeforeEach
    void seedProduct() throws Exception {
        productId = "API-KEY-EXPERIMENT-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO inventory_item (product_id, quantity, version) VALUES (?, ?, 0)")) {
            statement.setString(1, productId);
            statement.setInt(2, 10);
            statement.executeUpdate();
        }
    }

    @AfterEach
    void deleteProduct() throws Exception {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM inventory_item WHERE product_id = ?")) {
            statement.setString(1, productId);
            statement.executeUpdate();
        }
    }

    @Test
    void requestWithNoKeyIsRejected() throws Exception {
        HttpResponse<String> response = get(productId, null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void requestWithTheWrongKeyIsRejected() throws Exception {
        HttpResponse<String> response = get(productId, "not-the-real-key");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void requestWithTheCorrectKeySucceeds() throws Exception {
        HttpResponse<String> response = get(productId, CORRECT_KEY);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void theActuatorHealthCheckNeedsNoKeyAtAll() throws Exception {
        // The Compose healthcheck calls this with no idea this key exists -
        // it has to keep working regardless (see InternalApiKeyFilter's
        // shouldNotFilter).
        HttpResponse<String> response = getPath("/actuator/health", null);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String productId, String apiKey) throws Exception {
        return getPath("/inventory/" + productId, apiKey);
    }

    private HttpResponse<String> getPath(String path, String apiKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + path));
        if (apiKey != null) {
            builder.header("X-Internal-Api-Key", apiKey);
        }
        return httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
