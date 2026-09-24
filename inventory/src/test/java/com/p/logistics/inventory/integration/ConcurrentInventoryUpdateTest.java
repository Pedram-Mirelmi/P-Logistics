package com.p.logistics.inventory.integration;

import com.p.logistics.inventory.entity.InventoryItem;
import com.p.logistics.inventory.repository.JpaInventoryItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Experiment C (concurrent updates).
 * <p>
 * Two threads each open their own transaction (own connection, own Hibernate
 * session) against the same InventoryItem row. A CountDownLatch forces both
 * to read the row before either writes, so the interleaving is always the
 * worst case instead of down to thread-scheduling luck. Three variants of
 * the same race: no protection, {@code @Version}-based optimistic locking,
 * and pessimistic locking via {@code SELECT ... FOR UPDATE}.
 */
@SpringBootTest
class ConcurrentInventoryUpdateTest {

    private static final int STARTING_QUANTITY = 100;
    private static final int DECREMENT = 30;

    @Autowired
    private JpaInventoryItemRepository inventoryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private final List<String> seededProductIds = new ArrayList<>();

    private record AttemptResult(boolean committed, String failure) {
    }

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status ->
                seededProductIds.forEach(inventoryRepository::deleteById));
        seededProductIds.clear();
    }

    private String seed(String productId) {
        transactionTemplate.executeWithoutResult(status ->
                inventoryRepository.save(new InventoryItem(productId, STARTING_QUANTITY)));
        seededProductIds.add(productId);
        return productId;
    }

    @Test
    void noProtectionLosesAnUpdate() throws Exception {
        String productId = seed("EXP-C-NO-LOCK");
        CountDownLatch bothRead = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<AttemptResult> attempt = () -> transactionTemplate.execute(status -> {
            int current = inventoryRepository.findByProductId(productId).orElseThrow().getQuantity();

            bothRead.countDown();
            await(bothRead);

            // Blind bulk update: no "AND version = ?" in the WHERE clause,
            // so whichever thread's UPDATE runs last simply overwrites the
            // other's result -- there is nothing to detect the conflict.
            inventoryRepository.updateQuantityBlind(productId, current - DECREMENT);
            return new AttemptResult(true, null);
        });

        List<AttemptResult> results = runConcurrently(executor, attempt, attempt);
        int finalQuantity = inventoryRepository.findByProductId(productId).orElseThrow().getQuantity();

        System.out.println("[no protection] results=" + results + " finalQuantity=" + finalQuantity);

        // Both writers report success...
        assertThat(results).allMatch(AttemptResult::committed);
        // ...but only one decrement actually stuck: the lost-update bug.
        assertThat(finalQuantity).isEqualTo(STARTING_QUANTITY - DECREMENT);
    }

    @Test
    void optimisticLockingRejectsTheLoser() throws Exception {
        String productId = seed("EXP-C-OPTIMISTIC");
        CountDownLatch bothRead = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<AttemptResult> attempt = () -> {
            try {
                return transactionTemplate.execute(status -> {
                    InventoryItem item = inventoryRepository.findById(productId).orElseThrow();

                    bothRead.countDown();
                    await(bothRead);

                    // Mutating the managed entity is enough; Hibernate's
                    // dirty checking issues a version-checked UPDATE at
                    // commit, with no explicit save() call needed.
                    item.reserve(DECREMENT);
                    return new AttemptResult(true, null);
                });
            } catch (ObjectOptimisticLockingFailureException e) {
                return new AttemptResult(false, e.getClass().getSimpleName());
            }
        };

        List<AttemptResult> results = runConcurrently(executor, attempt, attempt);
        int finalQuantity = inventoryRepository.findByProductId(productId).orElseThrow().getQuantity();

        System.out.println("[optimistic] results=" + results + " finalQuantity=" + finalQuantity);

        assertThat(results).filteredOn(AttemptResult::committed).hasSize(1);
        assertThat(results)
                .filteredOn(r -> !r.committed())
                .hasSize(1)
                .allMatch(r -> "ObjectOptimisticLockingFailureException".equals(r.failure));
        // No lost update: exactly one decrement is ever persisted. The
        // loser's whole transaction rolled back -- this is exactly the
        // conflict ALLOWED_REATTEMPTS_NUMBER/@Retryable in InventoryService
        // exists to catch and retry.
        assertThat(finalQuantity).isEqualTo(STARTING_QUANTITY - DECREMENT);
    }

    @Test
    void pessimisticLockingSerializesInsteadOfFailing() throws Exception {
        String productId = seed("EXP-C-PESSIMISTIC");
        CountDownLatch lockAcquiredByFirstThread = new CountDownLatch(1);
        long holdMillis = 500;

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Void> firstHolder = () -> {
            transactionTemplate.executeWithoutResult(status -> {
                InventoryItem item = inventoryRepository.findByProductIdForUpdate(productId).orElseThrow();
                lockAcquiredByFirstThread.countDown();
                sleep(holdMillis); // simulate work while holding the row lock
                item.reserve(DECREMENT);
            });
            return null;
        };

        Callable<Long> secondWaiter = () -> {
            await(lockAcquiredByFirstThread);
            long start = System.nanoTime();
            transactionTemplate.executeWithoutResult(status -> {
                // Blocks at the DB level until the first transaction commits
                // and releases its row lock.
                InventoryItem item = inventoryRepository.findByProductIdForUpdate(productId).orElseThrow();
                item.reserve(DECREMENT);
            });
            return (System.nanoTime() - start) / 1_000_000;
        };

        Future<Void> firstFuture = executor.submit(firstHolder);
        Future<Long> secondFuture = executor.submit(secondWaiter);

        firstFuture.get(10, TimeUnit.SECONDS);
        long secondWaitMillis = secondFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        int finalQuantity = inventoryRepository.findByProductId(productId).orElseThrow().getQuantity();

        System.out.println("[pessimistic] secondThreadWaitedMs=" + secondWaitMillis + " finalQuantity=" + finalQuantity);

        // The second SELECT ... FOR UPDATE had to sit blocked for roughly as
        // long as the first transaction held the row lock (small margin for
        // scheduling jitter).
        assertThat(secondWaitMillis).isGreaterThanOrEqualTo(holdMillis - 100);
        // Both decrements are safely applied, one after the other -- no lost
        // update, and no exception/retry needed either.
        assertThat(finalQuantity).isEqualTo(STARTING_QUANTITY - 2 * DECREMENT);
    }

    @SafeVarargs
    private <T> List<T> runConcurrently(ExecutorService executor, Callable<T>... tasks) throws Exception {
        List<Future<T>> futures = executor.invokeAll(List.of(tasks));
        executor.shutdown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        return results;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the other thread to catch up");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
