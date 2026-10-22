package com.p.logistics.order.dto;

import com.p.logistics.order.entity.OrderLine;

import java.util.List;

public record CreateOrderRequest(
        List<OrderLine> lines
) {}
