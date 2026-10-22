package com.p.logistics.inventory.messaging.event;

import com.p.logistics.inventory.inventory_item.dto.OrderItem;

import java.util.List;

public record OrderRegisteredEvent(String orderId, List<OrderItem> orderItems) {
}
