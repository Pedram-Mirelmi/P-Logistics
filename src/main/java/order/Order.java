package order;

import java.util.List;
import java.util.UUID;

public class Order {

    private final UUID id;
    private final List<OrderLine> lines;
    private final OrderStatus status;

    public Order(UUID id, List<OrderLine> lines, OrderStatus status) {
        this.id = id;
        this.lines = lines;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public OrderStatus getStatus() {
        return status;
    }
}