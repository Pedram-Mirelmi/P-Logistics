package com.p.logistics.order;

public record OrderLine(
        String productId,
        int quantity
) {}