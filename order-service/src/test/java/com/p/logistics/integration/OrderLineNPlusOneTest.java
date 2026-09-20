package com.p.logistics.integration;

import com.p.logistics.order.JpaOrderRepository;
import com.p.logistics.order.Order;
import com.p.logistics.order.OrderLine;
import com.p.logistics.order.OrderStatus;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Experiment A (N+1).
 * <p>
 * Run with -Dspring.jpa.show-sql=true (already on in application.properties)
 * and watch stdout: the first test prints one SELECT for the orders followed
 * by one SELECT per order for its lines; the second prints a single SELECT
 * with a JOIN. The assertions on {@link Statistics#getPrepareStatementCount()}
 * make that difference a hard number instead of something you have to eyeball.
 */
@SpringBootTest
class OrderLineNPlusOneTest {

    private static final int ORDER_COUNT = 5;

    @Autowired
    private JpaOrderRepository orderRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Statistics statistics;
    private List<UUID> seededOrderIds;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        seededOrderIds = transactionTemplate.execute(status -> {
            List<Order> saved = new ArrayList<>();
            for (int i = 0; i < ORDER_COUNT; i++) {
                Order order = new Order(
                        UUID.randomUUID(),
                        List.of(new OrderLine("N-PLUS-ONE-PRODUCT-" + i, 1)),
                        OrderStatus.ACCEPTED
                );
                saved.add(orderRepository.save(order));
            }
            return saved.stream().map(Order::getId).toList();
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.execute(status -> {
            seededOrderIds.forEach(orderRepository::deleteById);
            return null;
        });
    }

    @Test
    void naiveFindAllTriggersOneLazyLoadQueryPerOrder() {
        statistics.clear();

        transactionTemplate.execute(status -> {
            List<Order> orders = orderRepository.findAll();
            // Accessing the lazy collection inside the same transaction is
            // what fires the extra per-order SELECT.
            orders.forEach(order -> order.getLines().size());
            return null;
        });

        long statementCount = statistics.getPrepareStatementCount();
        System.out.println("findAll() + lazy lines -> statements executed: " + statementCount);

        // 1 query for the orders themselves + at least 1 per order for its lines.
        assertThat(statementCount).isGreaterThanOrEqualTo(ORDER_COUNT + 1);
    }

    @Test
    void fetchJoinCollapsesToASingleQuery() {
        statistics.clear();

        transactionTemplate.execute(status -> {
            List<Order> orders = orderRepository.findAllWithLines();
            orders.forEach(order -> order.getLines().size());
            return null;
        });

        long statementCount = statistics.getPrepareStatementCount();
        System.out.println("findAllWithLines() (fetch join) -> statements executed: " + statementCount);

        assertThat(statementCount).isEqualTo(1);
    }
}
