package com.p.logistics.unit;

import com.p.logistics.inventory.InventoryItem;
import com.p.logistics.inventory.InventoryReservation;
import com.p.logistics.inventory.InventoryTransactionService;
import com.p.logistics.inventory.JpaInventoryItemRepository;
import com.p.logistics.inventory.JpaInventoryReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test: both repositories are Mockito mocks, so no transaction and
 * no real persistence happen here - this only checks the reservation
 * decision itself (in-memory InventoryItem.reserve()) and which repository
 * calls follow from it. The concurrency behavior around this same method is
 * covered separately by the real-database ConcurrentInventoryUpdateTest.
 */
@ExtendWith(MockitoExtension.class)
class InventoryTransactionServiceTest {

    @Mock
    private JpaInventoryItemRepository inventoryRepository;

    @Mock
    private JpaInventoryReservationRepository reservationRepository;

    private InventoryTransactionService inventoryTransactionService;

    private InventoryTransactionService newTransactionService() {
        return new InventoryTransactionService(inventoryRepository, reservationRepository);
    }

    @Test
    void reserveWithinStockReturnsTrueAndSavesReservation() {
        inventoryTransactionService = newTransactionService();
        when(inventoryRepository.findById("P1")).thenReturn(Optional.of(new InventoryItem("P1", 100)));

        boolean reserved = inventoryTransactionService.reserve("P1", 30);

        assertThat(reserved).isTrue();

        ArgumentCaptor<InventoryReservation> captor = ArgumentCaptor.forClass(InventoryReservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo("P1");
        assertThat(captor.getValue().getQuantity()).isEqualTo(30);
    }

    @Test
    void reserveExceedingStockReturnsFalseAndSavesNoReservation() {
        inventoryTransactionService = newTransactionService();
        when(inventoryRepository.findById("P1")).thenReturn(Optional.of(new InventoryItem("P1", 10)));

        boolean reserved = inventoryTransactionService.reserve("P1", 30);

        assertThat(reserved).isFalse();
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void reserveForUnknownProductReturnsFalseAndSavesNoReservation() {
        inventoryTransactionService = newTransactionService();
        when(inventoryRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        boolean reserved = inventoryTransactionService.reserve("UNKNOWN", 1);

        assertThat(reserved).isFalse();
        verifyNoInteractions(reservationRepository);
    }
}
