package com.p.logistics.inventory.config;

import com.p.logistics.inventory.entity.InventoryItem;
import com.p.logistics.inventory.repository.JpaInventoryItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// Runs once on startup against an empty table, so there is always a known
// product catalog to call /inventory/{productId} or /reserve against
// without a manual psql insert first.
@Slf4j
@Component
public class InventoryDataSeeder implements CommandLineRunner {

    private final JpaInventoryItemRepository inventoryRepository;

    public InventoryDataSeeder(JpaInventoryItemRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(String... args) {
        if (inventoryRepository.count() > 0) {
            return;
        }

        inventoryRepository.saveAll(List.of(
                new InventoryItem("SKU-1", 100),
                new InventoryItem("SKU-2", 50),
                new InventoryItem("SKU-3", 200)
        ));

        log.info("Seeded inventory catalog: SKU-1=100, SKU-2=50, SKU-3=200");
    }
}
