package com.p.logistics.order.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p.logistics.order.client.InventoryClient;
import com.p.logistics.order.client.ReservationOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Inventory is now a separate deployed process (see InventoryClient), so this
// test can no longer rely on a shared local transaction/DB row to make
// reservation succeed. It stubs InventoryClient instead, keeping this test
// focused on what order-service itself owns: accepting an order and
// triggering shipment creation.
//
// Since Step 07, shipment creation is no longer inline with order
// submission - OrderService only records an outbox event; OutboxRelay
// publishes it to RabbitMQ on its own schedule, and OrderAcceptedListener
// creates the shipment after consuming it (see those classes). So the
// shipment genuinely may not exist the instant POST /orders returns - this
// test polls instead of asserting immediately, which is the correct way to
// assert on an asynchronous flow instead of just hoping it's fast enough.
// This also means the test now needs a real RabbitMQ, not just Postgres
// (docker compose up).
@SpringBootTest
@AutoConfigureMockMvc
class OrderFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InventoryClient inventoryClient;

    @Test
    void acceptedOrderCreatesShipment() throws Exception {

        Mockito.when(inventoryClient.batchReserve(ArgumentMatchers.any())).thenReturn(ReservationOutcome.RESERVED);

        String response = mockMvc.perform(post("/orders")
                        .with(httpBasic("order-client", "order-client-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lines": [
                                    {
                                      "productId": "P1",
                                      "quantity": 2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode order = objectMapper.readTree(response);
        String orderId = order.get("id").asText();

        // ignoreExceptions(): before the shipment exists,
        // ShipmentService.getShipmentByOrderId throws IllegalArgumentException,
        // and MockMvc propagates that out of perform() as a ServletException
        // rather than a 404 response - untilAsserted only retries on
        // AssertionError by default, so without this the first poll's
        // exception would abort the whole wait instead of being treated as
        // "not yet".
        await().atMost(Duration.ofSeconds(10)).ignoreExceptions().untilAsserted(() ->
                mockMvc.perform(get("/shipments/order/" + orderId)
                                .with(httpBasic("order-client", "order-client-secret")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.orderId").value(orderId))
                        .andExpect(jsonPath("$.status").value("CREATED")));
    }
}