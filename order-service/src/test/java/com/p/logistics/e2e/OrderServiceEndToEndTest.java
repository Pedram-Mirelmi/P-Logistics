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
 * End-to-end: drives the real, already-running order-service HTTP API (see
 * compose.yaml), which in turn makes a real RPC call over HTTP to the real,
 * already-running inventory-service - exercising the full distributed path,
 * not a mocked InventoryClient. Not a @SpringBootTest - no context is
 * started here.
 * <p>
 * Requires {@code docker compose up} to be running: order-service on
 * localhost:8080, inventory-service on localhost:8081, postgres-inventory on
 * localhost:5434 (seeded here directly since order-service owns none of
 * inventory-service's data).
 */
class OrderServiceEndToEndTest {

    private static final String ORDER_BASE_URL = "http://localhost:8080";
    private static final String INVENTORY_DB_URL = "jdbc:postgresql://localhost:5434/inventory";
    private static final String INVENTORY_DB_USER = "inventory";
    private static final String INVENTORY_DB_PASSWORD = "inventory";
    private static final int SEEDED_QUANTITY = 20;

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String productId;

    @BeforeEach
    void seedInventory() throws Exception {
        productId = "E2E-ORDER-" + UUID.randomUUID();

        try (Connection connection = DriverManager.getConnection(INVENTORY_DB_URL, INVENTORY_DB_USER, INVENTORY_DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO inventory_item (product_id, quantity, version) VALUES (?, ?, 0)")) {
            statement.setString(1, productId);
            statement.setInt(2, SEEDED_QUANTITY);
            statement.executeUpdate();
        }
    }

    @AfterEach
    void deleteInventory() throws Exception {
        try (Connection connection = DriverManager.getConnection(INVENTORY_DB_URL, INVENTORY_DB_USER, INVENTORY_DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM inventory_item WHERE product_id = ?")) {
            statement.setString(1, productId);
            statement.executeUpdate();
        }
    }

    @Test
    void orderWithinStockIsAcceptedAndCreatesShipment() throws Exception {
        HttpResponse<String> orderResponse = post("/orders", """
                {"lines":[{"productId":"%s","quantity":5}]}
                """.formatted(productId));

        assertThat(orderResponse.statusCode()).isEqualTo(200);
        JsonNode order = bodyOf(orderResponse);
        assertThat(order.get("status").asText()).isEqualTo("ACCEPTED");

        String orderId = order.get("id").asText();
        HttpResponse<String> shipmentResponse = get("/shipments/order/" + orderId);
        assertThat(shipmentResponse.statusCode()).isEqualTo(200);
        assertThat(bodyOf(shipmentResponse).get("status").asText()).isEqualTo("CREATED");
    }

    @Test
    void orderExceedingStockIsRejectedAndCreatesNoShipment() throws Exception {
        HttpResponse<String> orderResponse = post("/orders", """
                {"lines":[{"productId":"%s","quantity":9999}]}
                """.formatted(productId));

        JsonNode order = bodyOf(orderResponse);
        assertThat(order.get("status").asText()).isEqualTo("REJECTED");

        String orderId = order.get("id").asText();
        HttpResponse<String> shipmentResponse = get("/shipments/order/" + orderId);
        assertThat(shipmentResponse.statusCode()).isGreaterThanOrEqualTo(400);
    }

    @Test
    void orderForUnknownProductIsRejected() throws Exception {
        HttpResponse<String> orderResponse = post("/orders", """
                {"lines":[{"productId":"does-not-exist-%s","quantity":1}]}
                """.formatted(UUID.randomUUID()));

        JsonNode order = bodyOf(orderResponse);
        assertThat(order.get("status").asText()).isEqualTo("REJECTED");
    }

    private JsonNode bodyOf(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ORDER_BASE_URL + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ORDER_BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
