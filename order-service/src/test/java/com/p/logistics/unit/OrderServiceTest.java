package com.p.logistics.unit;

import com.p.logistics.inventory.InventoryClient;
import com.p.logistics.inventory.InventoryUnavailableException;
import com.p.logistics.order.JpaOrderRepository;
import com.p.logistics.order.Order;
import com.p.logistics.order.OrderLine;
import com.p.logistics.order.OrderService;
import com.p.logistics.order.OrderStatus;
import com.p.logistics.shipment.ShipmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test: every collaborator (repository, InventoryClient,
 * ShipmentService) is a Mockito mock, so this exercises only OrderService's
 * own decision logic - no Spring context, no database, no network call.
 * Compare with the integration/e2e tests, which exercise the same behavior
 * through progressively more of the real stack.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private JpaOrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private ShipmentService shipmentService;

    private OrderService orderService;

    private OrderService newOrderService() {
        return new OrderService(orderRepository, inventoryClient, shipmentService);
    }

    @Test
    void allLinesReservedAcceptsOrderAndCreatesShipment() {
        orderService = newOrderService();
        when(inventoryClient.reserve("P1", 2)).thenReturn(true);

        Order order = orderService.submitOrder(List.of(new OrderLine("P1", 2)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        verify(orderRepository).save(order);
        verify(shipmentService).createShipment(order.getId(), "DEFAULT_DESTINATION");
    }

    @Test
    void oneLineNotReservedRejectsOrderAndSkipsShipment() {
        orderService = newOrderService();
        when(inventoryClient.reserve("P1", 2)).thenReturn(false);

        Order order = orderService.submitOrder(List.of(new OrderLine("P1", 2)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verify(orderRepository).save(order);
        verifyNoInteractions(shipmentService);
    }

    @Test
    void inventoryUnavailableIsTreatedAsRejectionNotAFailure() {
        orderService = newOrderService();
        when(inventoryClient.reserve("P1", 2))
                .thenThrow(new InventoryUnavailableException("P1", new RuntimeException("connection refused")));

        Order order = orderService.submitOrder(List.of(new OrderLine("P1", 2)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verifyNoInteractions(shipmentService);
    }

    @Test
    void secondLineNeverReservedOnceAnEarlierLineFails() {
        orderService = newOrderService();
        when(inventoryClient.reserve("P1", 1)).thenReturn(false);

        orderService.submitOrder(List.of(new OrderLine("P1", 1), new OrderLine("P2", 1)));

        verify(inventoryClient).reserve("P1", 1);
        verify(inventoryClient, never()).reserve("P2", 1);
    }

    @Test
    void getOrderThrowsWhenNotFound() {
        orderService = newOrderService();
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(id))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
