package com.p.logistics.inventory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class InventoryDataInitializer implements CommandLineRunner {

    private final InventoryRepository inventoryRepository;

    public InventoryDataInitializer(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(String... args) {
        inventoryRepository.save(new InventoryItem("P1", 10));
        inventoryRepository.save(new InventoryItem("P2", 5));
        inventoryRepository.save(new InventoryItem("P3", 20));
    }
}