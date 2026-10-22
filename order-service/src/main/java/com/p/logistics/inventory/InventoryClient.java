package com.p.logistics.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

    public boolean reserve(String productId, int quantity) {
        try {
            ReserveInventoryResponse response = restClient.post()
                    .uri("/inventory/{productId}/reserve", productId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReserveInventoryRequest(quantity))
                    .retrieve()
                    .body(ReserveInventoryResponse.class);

            return response != null && response.reserved();
        } catch (RestClientException e) {
            throw new InventoryUnavailableException(productId, e);
        }
    }
}
