package com.p.logistics.order.unit;

import com.p.logistics.order.client.InventoryClient;
import com.p.logistics.order.client.InventoryUnavailableException;
import com.p.logistics.order.client.ReservationOutcome;
import com.p.logistics.order.dto.ReserveInventoryRequest;
import com.p.logistics.order.entity.Order;
import com.p.logistics.order.entity.OrderLine;
import com.p.logistics.order.entity.OrderStatus;
import com.p.logistics.order.repository.JpaOrderRepository;
import com.p.logistics.order.service.OrderService;
import com.p.logistics.order.service.OutboxEventRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test: every collaborator (repository, InventoryClient,
 * OutboxEventRecorder) is a Mockito mock, so this exercises only
 * OrderService's own decision logic - no Spring context, no database, no
 * network call. Compare with the integration/e2e tests, which exercise the
 * same behavior through progressively more of the real stack.
 * <p>
 * Since Step 07, "creates shipment" here means "records the OrderAccepted
 * outbox event" - actual shipment creation moved to OrderAcceptedListener,
 * consuming that event asynchronously. See OutboxRelay/OrderAcceptedListener.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private JpaOrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OutboxEventRecorder outboxEventRecorder;

    private OrderService orderService;

    private OrderService newOrderService() {
        return new OrderService(orderRepository, inventoryClient, outboxEventRecorder);
    }

    @Test
    void allLinesReservedAcceptsOrderAndRecordsOrderAcceptedEvent() {
        orderService = newOrderService();
        when(inventoryClient.batchReserve(any())).thenReturn(ReservationOutcome.RESERVED);

        Order order = orderService.submitOrder(List.of(new OrderLine("P1", 2)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        verify(orderRepository).save(order);
        verify(outboxEventRecorder).recordOrderAccepted(order.getId(), "DEFAULT_DESTINATION");
    }

    @Test
    void insufficientStockRejectsOrderAndSkipsEvent() {
        orderService = newOrderService();
        when(inventoryClient.batchReserve(any())).thenReturn(ReservationOutcome.INSUFFICIENT_STOCK);

        Order order = orderService.submitOrder(List.of(new OrderLine("P1", 2)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verify(orderRepository).save(order);
        verifyNoInteractions(outboxEventRecorder);
    }

    @Test
    void unknownProductRejectsOrderAndSkipsEvent() {
        orderService = newOrderService();
        when(inventoryClient.batchReserve(any())).thenReturn(ReservationOutcome.UNKNOWN_PRODUCT);

        Order order = orderService.submitOrder(List.of(new OrderLine("P1", 2)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verifyNoInteractions(outboxEventRecorder);
    }

    @Test
    void inventoryUnavailableIsTreatedAsRejectionNotAFailure() {
        orderService = newOrderService();
        when(inventoryClient.batchReserve(any()))
                .thenThrow(new InventoryUnavailableException(new RuntimeException("connection refused")));

        Order order = orderService.submitOrder(List.of(new OrderLine("P1", 2)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verifyNoInteractions(outboxEventRecorder);
    }

    @Test
    void allLinesGoToInventoryInOneBatchCall() {
        orderService = newOrderService();
        when(inventoryClient.batchReserve(any())).thenReturn(ReservationOutcome.RESERVED);

        orderService.submitOrder(List.of(new OrderLine("P1", 1), new OrderLine("P2", 3)));

        verify(inventoryClient).batchReserve(List.of(
                new ReserveInventoryRequest("P1", 1),
                new ReserveInventoryRequest("P2", 3)));
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
