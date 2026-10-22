package com.p.logistics.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p.logistics.inventory.InventoryClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Inventory is now a separate deployed process (see InventoryClient), so this
// test can no longer rely on a shared local transaction/DB row to make
// reservation succeed. It stubs InventoryClient instead, keeping this test
// focused on what order-service itself owns: accepting an order and
// triggering shipment creation. Inventory's own reservation logic is
// covered by inventory-service's own tests.
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

        Mockito.when(inventoryClient.reserve("P1", 2)).thenReturn(true);

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

        mockMvc.perform(get("/shipments/order/" + orderId)
                        .with(httpBasic("order-client", "order-client-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }
}