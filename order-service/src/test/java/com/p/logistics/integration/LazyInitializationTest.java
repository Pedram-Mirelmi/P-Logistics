package com.p.logistics.integration;

import com.p.logistics.order.JpaOrderRepository;
import com.p.logistics.order.Order;
import com.p.logistics.order.OrderLine;
import com.p.logistics.order.OrderStatus;
import org.hibernate.LazyInitializationException;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Experiment B (lazy loading outside the persistence context).
 * <p>
 * Spring Data wraps every {@code JpaRepository} CRUD method in its own
 * short-lived transaction (class-level {@code @Transactional(readOnly = true)}
 * on {@code SimpleJpaRepository}). By the time a call like
 * {@code orderRepository.findById(id)} returns to this test, that
 * transaction — and the Hibernate Session bound to it — has already been
 * committed and closed. Nothing extra needs to be done to "close the
 * session"; simply calling the repository from outside any surrounding
 * {@code @Transactional} already does it.
 */
@SpringBootTest
class LazyInitializationTest {

    @Autowired
    private JpaOrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        orderId = transactionTemplate.execute(status -> {
            Order order = new Order(
                    UUID.randomUUID(),
                    List.of(new OrderLine("LAZY-TEST-PRODUCT", 1)),
                    OrderStatus.ACCEPTED
            );
            return orderRepository.save(order).getId();
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> orderRepository.deleteById(orderId));
    }

    @Test
    void loadedRootEntityIsNotAProxyButItsLazyCollectionIs() {
        // findById() issues a real SELECT for the Order's own columns, so the
        // object returned is a fully-loaded instance, not a proxy...
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order).isNotInstanceOf(HibernateProxy.class);

        // ...but Hibernate never eagerly loads the @OneToMany "lines". It
        // installs a lazy-initializing collection wrapper in that field
        // instead, and defers the SELECT until something touches it.
        List<OrderLine> lines = order.getLines();

        // Touching it now, after findById()'s transaction/session has
        // already closed, has nowhere to run that deferred query.
        assertThatThrownBy(lines::size)
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("no Session");
    }

    @Test
    void getReferenceByIdReturnsAProxyForTheRootEntityItself() {
        // Unlike findById(), getReferenceById() never hits the DB at all: it
        // hands back a Hibernate-generated proxy that only knows the id.
        Order reference = orderRepository.getReferenceById(orderId);
        assertThat(reference).isInstanceOf(HibernateProxy.class);

        // Reading the id needs no query -- it's the one field the proxy is
        // seeded with -- so this works even with the session long closed.
        assertThat(reference.getId()).isEqualTo(orderId);

        // Reading any other field forces Hibernate to initialize the proxy's
        // real target with a SELECT ... same "no Session" failure as above,
        // just for the root entity instead of one of its collections.
        assertThatThrownBy(reference::getStatus)
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("no session");
    }

    @Test
    void accessingTheSameLazyCollectionInsideItsSessionWorksFine() {
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            // Same code as the failing test above, but the session backing
            // this Order is still open here, so the deferred SELECT has
            // somewhere to run.
            assertThat(order.getLines()).hasSize(1);
        });
    }
}
