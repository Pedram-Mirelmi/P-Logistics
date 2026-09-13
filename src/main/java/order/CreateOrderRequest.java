package order;

import java.util.List;

public record CreateOrderRequest(
        List<OrderLine> lines
) {}
