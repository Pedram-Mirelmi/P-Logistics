package com.p.logistics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@SpringBootTest
class ConnectionPoolDemoTest {

    @Autowired
    private DataSource dataSource;

    private static final int REQUEST_COUNT = 100;
    private static final int CLIENT_THREADS = 50;

    @Test
    void connectionPoolLoadTest() throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(CLIENT_THREADS);

        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successful = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        LongAdder totalLatencyMs = new LongAdder();

        Map<String, AtomicInteger> failures =
                new ConcurrentHashMap<>();

        long experimentStart = System.currentTimeMillis();

        for (int i = 0; i < REQUEST_COUNT; i++) {

            executor.submit(() -> {

                try {
                    startGate.await();

                    long start = System.currentTimeMillis();

                    /*
                     * pg_sleep(1) deliberately holds the DB connection
                     * for one second.
                     */
                    try (
                            Connection connection = dataSource.getConnection();
                            PreparedStatement statement =
                                    connection.prepareStatement(
                                            "SELECT pg_sleep(1)"
                                    )
                    ) {
                        statement.execute();
                    }

                    long latency =
                            System.currentTimeMillis() - start;

                    totalLatencyMs.add(latency);
                    successful.incrementAndGet();

                } catch (Exception e) {

                    failed.incrementAndGet();

                    failures
                            .computeIfAbsent(
                                    e.getClass().getSimpleName(),
                                    key -> new AtomicInteger()
                            )
                            .incrementAndGet();
                }
            });
        }

        // Release all requests at approximately the same time
        startGate.countDown();

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);

        long totalExperimentTime =
                System.currentTimeMillis() - experimentStart;

        int successCount = successful.get();
        int failureCount = failed.get();

        double successRate =
                100.0 * successCount / REQUEST_COUNT;

        double averageLatency =
                successCount == 0
                        ? 0
                        : (double) totalLatencyMs.sum() / successCount;

        System.out.println();
        System.out.println("========== CONNECTION POOL TEST ==========");
        System.out.println("Requests:          " + REQUEST_COUNT);
        System.out.println("Client threads:    " + CLIENT_THREADS);
        System.out.println("Successful:        " + successCount);
        System.out.println("Failed:            " + failureCount);
        System.out.printf ("Success rate:      %.2f%%%n", successRate);
        System.out.printf ("Average latency:   %.2f ms%n", averageLatency);
        System.out.println("Total duration:    " + totalExperimentTime + " ms");

        if (!failures.isEmpty()) {
            System.out.println();
            System.out.println("Failures:");

            failures.forEach((type, count) ->
                    System.out.println(
                            "  " + type + ": " + count.get()
                    )
            );
        }

        System.out.println("==========================================");
    }
}