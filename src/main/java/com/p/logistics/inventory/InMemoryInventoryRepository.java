package com.p.logistics.inventory;


import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryInventoryRepository implements InventoryRepository {

    private final Map<String, InventoryItem> items = new ConcurrentHashMap<>();

    @Override
    public Optional<InventoryItem> findByProductId(String productId) {
        return Optional.ofNullable(items.get(productId));
    }

    @Override
    public void save(InventoryItem item) {
        items.put(item.getProductId(), item);
    }

    @Override
    public boolean reserve(String productId, int quantity) {
        InventoryItem item = items.get(productId);

        if (item == null) {
            return false;
        }

        return item.reserve(quantity);
    }
}
