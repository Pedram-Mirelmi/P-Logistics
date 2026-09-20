package com.p.logistics.unit;

import com.p.logistics.inventory.InventoryItem;
import com.p.logistics.inventory.InventoryService;
import com.p.logistics.inventory.InventoryTransactionService;
import com.p.logistics.inventory.JpaInventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test: repository and InventoryTransactionService are Mockito
 * mocks, so this exercises only InventoryService's own logic - the lookup
 * and the delegation - not persistence or transaction behavior (that's
 * InventoryTransactionServiceTest).
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private JpaInventoryItemRepository inventoryRepository;

    @Mock
    private InventoryTransactionService inventoryTransactionService;

    private InventoryService inventoryService;

    private InventoryService newInventoryService() {
        return new InventoryService(inventoryRepository, inventoryTransactionService);
    }

    @Test
    void getInventoryReturnsItemWhenFound() {
        inventoryService = newInventoryService();
        InventoryItem item = new InventoryItem("P1", 42);
        when(inventoryRepository.findByProductId("P1")).thenReturn(Optional.of(item));

        assertThat(inventoryService.getInventory("P1")).isSameAs(item);
    }

    @Test
    void getInventoryThrowsWhenProductNotFound() {
        inventoryService = newInventoryService();
        when(inventoryRepository.findByProductId("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getInventory("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserveDelegatesToTransactionServiceAndReturnsItsResult() {
        inventoryService = newInventoryService();
        when(inventoryTransactionService.reserve("P1", 5)).thenReturn(true);

        assertThat(inventoryService.reserve("P1", 5)).isTrue();
        verify(inventoryTransactionService).reserve("P1", 5);
    }
}
