package com.p.logistics.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * End-to-end: exercises the real, already-running inventory-service HTTP API
 * (see compose.yaml) against its own real Postgres database, exactly like an
 * external client would. Not a @SpringBootTest - no context is started here.
 * <p>
 * Requires {@code docker compose up} to be running: inventory-service on
 * localhost:8081, postgres-inventory on localhost:5434.
 */
class InventoryServiceEndToEndTest {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String DB_URL = "jdbc:postgresql://localhost:5434/inventory";
    private static final String DB_USER = "inventory";
    private static final String DB_PASSWORD = "inventory";
    private static final int SEEDED_QUANTITY = 50;

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String productId;

    @BeforeEach
    void seedProduct() throws Exception {
        productId = "E2E-" + UUID.randomUUID();

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO inventory_item (product_id, quantity, version) VALUES (?, ?, 0)")) {
            statement.setString(1, productId);
            statement.setInt(2, SEEDED_QUANTITY);
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
    void getInventoryReturnsSeededQuantity() throws Exception {
        HttpResponse<String> response = get("/inventory/" + productId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(bodyOf(response).get("quantity").asInt()).isEqualTo(SEEDED_QUANTITY);
    }

    @Test
    void reserveWithinStockSucceedsAndDecrementsQuantity() throws Exception {
        HttpResponse<String> reserveResponse = post("/inventory/" + productId + "/reserve", "{\"quantity\":10}");

        assertThat(reserveResponse.statusCode()).isEqualTo(200);
        assertThat(bodyOf(reserveResponse).get("reserved").asBoolean()).isTrue();

        HttpResponse<String> getResponse = get("/inventory/" + productId);
        assertThat(bodyOf(getResponse).get("quantity").asInt()).isEqualTo(SEEDED_QUANTITY - 10);
    }

    @Test
    void reserveMoreThanAvailableFailsAndLeavesQuantityUnchanged() throws Exception {
        HttpResponse<String> reserveResponse = post("/inventory/" + productId + "/reserve", "{\"quantity\":9999}");

        assertThat(bodyOf(reserveResponse).get("reserved").asBoolean()).isFalse();

        HttpResponse<String> getResponse = get("/inventory/" + productId);
        assertThat(bodyOf(getResponse).get("quantity").asInt()).isEqualTo(SEEDED_QUANTITY);
    }

    @Test
    void getUnknownProductFails() throws Exception {
        HttpResponse<String> response = get("/inventory/does-not-exist-" + UUID.randomUUID());

        assertThat(response.statusCode()).isGreaterThanOrEqualTo(400);
    }

    private JsonNode bodyOf(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
