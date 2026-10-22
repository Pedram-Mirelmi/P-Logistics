package com.p.logistics.order.client;

import com.p.logistics.order.dto.ReserveInventoryRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class InventoryClient {

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;

    public InventoryClient(
            @Value("${inventory.service.url}") String inventoryServiceUrl,
            @Value("${internal.api-key}") String internalApiKey
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        this.restClient = RestClient.builder()
                .baseUrl(inventoryServiceUrl)
                .requestFactory(requestFactory)
                .defaultHeader(API_KEY_HEADER, internalApiKey)
                .build();
    }

    // All lines are reserved or none are - inventory-service does the whole
    // batch in one local transaction. Its contract: 200 true / 200 false
    // (insufficient stock) / 404 (unknown product); anything else means the
    // outcome is unknown.
    public ReservationOutcome batchReserve(List<ReserveInventoryRequest> lines) {
        try {
            return restClient.post()
                    .uri("/inventory/batch-reserve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(lines)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                            return ReservationOutcome.UNKNOWN_PRODUCT;
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new InventoryUnavailableException(
                                    "Inventory service answered batch-reserve with " + response.getStatusCode());
                        }
                        Boolean reserved = response.bodyTo(Boolean.class);
                        if (reserved == null) {
                            throw new InventoryUnavailableException("Inventory service sent an empty batch-reserve body");
                        }
                        return reserved ? ReservationOutcome.RESERVED : ReservationOutcome.INSUFFICIENT_STOCK;
                    });
        } catch (RestClientException e) {
            throw new InventoryUnavailableException(e);
        }
    }
}
