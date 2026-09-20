package com.p.logistics.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor
public class Order {

    @Id
    private UUID id;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public Order(UUID id, List<OrderLine> lines, OrderStatus status) {
        this.id = id;
        this.status = status;
        setLines(lines);
    }

    private void setLines(List<OrderLine> lines) {
        this.lines = lines;
        lines.forEach(line -> line.setOrder(this));
    }
}
