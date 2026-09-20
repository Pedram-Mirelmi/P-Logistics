package com.p.logistics.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Experiment D (isolation anomalies).
 * <p>
 * Nothing in application.properties sets an isolation level (no
 * {@code @Transactional(isolation = ...)}, no Hikari override), so every
 * connection runs at whatever Postgres's server default is -- READ
 * COMMITTED out of the box. This test first shows the anomaly that level
 * permits (a non-repeatable read), then reruns the identical interleaving
 * at REPEATABLE READ to show Postgres's snapshot isolation prevents it.
 */
@SpringBootTest
class IsolationAnomalyTest {

    private static final String PRODUCT_ID = "EXP-D-ISOLATION";
    private static final int INITIAL_QUANTITY = 100;
    private static final int UPDATED_QUANTITY = 999;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void seed() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            deleteRow(connection);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO inventory_item (product_id, quantity, version) VALUES (?, ?, 0)")) {
                insert.setString(1, PRODUCT_ID);
                insert.setInt(2, INITIAL_QUANTITY);
                insert.executeUpdate();
            }
        }
    }

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            deleteRow(connection);
        }
    }

    private void deleteRow(Connection connection) throws Exception {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM inventory_item WHERE product_id = ?")) {
            delete.setString(1, PRODUCT_ID);
            delete.executeUpdate();
        }
    }

    @Test
    void readCommittedAllowsANonRepeatableRead() throws Exception {
        int[] reads = runInterleaving(Connection.TRANSACTION_READ_COMMITTED);

        System.out.println("[READ COMMITTED] firstRead=" + reads[0] + " secondRead=" + reads[1]);

        assertThat(reads[0]).isEqualTo(INITIAL_QUANTITY);
        // The reader's own transaction never wrote anything, yet its second
        // SELECT sees the writer's committed change: same transaction, two
        // different answers to the exact same query. That's a non-repeatable
        // read, and READ COMMITTED is defined to allow it.
        assertThat(reads[1]).isEqualTo(UPDATED_QUANTITY);
    }

    @Test
    void repeatableReadHidesTheConcurrentCommit() throws Exception {
        int[] reads = runInterleaving(Connection.TRANSACTION_REPEATABLE_READ);

        System.out.println("[REPEATABLE READ] firstRead=" + reads[0] + " secondRead=" + reads[1]);

        assertThat(reads[0]).isEqualTo(INITIAL_QUANTITY);
        // Postgres takes an MVCC snapshot at the start of the transaction and
        // reuses it for every statement in that transaction, so the second
        // read is frozen at the same value as the first even though the
        // writer committed a change in between -- the anomaly above is gone.
        assertThat(reads[1]).isEqualTo(INITIAL_QUANTITY);
    }

    /**
     * Runs one reader transaction, at the given isolation level, that SELECTs
     * the row twice with a writer transaction committing an update to that
     * row in between. Returns {@code [firstRead, secondRead]}.
     */
    private int[] runInterleaving(int isolationLevel) throws Exception {
        CountDownLatch firstReadDone = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        int[] reads = new int[2];

        Runnable reader = () -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(isolationLevel);

                reads[0] = readQuantity(connection);
                firstReadDone.countDown();

                await(writerCommitted);
                reads[1] = readQuantity(connection);

                connection.commit();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        Runnable writer = () -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);

                await(firstReadDone);

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE inventory_item SET quantity = ? WHERE product_id = ?")) {
                    update.setInt(1, UPDATED_QUANTITY);
                    update.setString(2, PRODUCT_ID);
                    update.executeUpdate();
                }
                connection.commit();
                writerCommitted.countDown();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> readerFuture = executor.submit(reader);
        Future<?> writerFuture = executor.submit(writer);

        readerFuture.get(10, TimeUnit.SECONDS);
        writerFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        return reads;
    }

    private int readQuantity(Connection connection) throws Exception {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT quantity FROM inventory_item WHERE product_id = ?")) {
            select.setString(1, PRODUCT_ID);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
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
}
