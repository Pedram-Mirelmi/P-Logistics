package com.p.logistics.experiment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Experiment F (Step 05's "Distributed transaction lab").
 * <p>
 * postgres-logistics (order-service's database) and postgres-inventory
 * (inventory-service's database) are two genuinely independent resource
 * managers - separate containers, separate connections, no shared
 * transaction. This test plays coordinator by hand, driving Postgres's
 * native two-phase commit SQL ({@code PREPARE TRANSACTION} /
 * {@code COMMIT PREPARED} / {@code ROLLBACK PREPARED}) directly against
 * both, so "prepare", "vote", "commit/abort", "coordinator failure", and
 * "participant uncertainty" are things you watch happen instead of terms
 * to memorize.
 * <p>
 * None of this is wired into the real application. {@code OrderService}
 * still has no atomicity between its own insert and the remote reservation
 * call (see ADR-005) - that gap is deliberate. This lab exists to explain
 * <i>why</i> closing it with 2PC is not the path this system takes: every
 * test below needs both resources reachable, both willing to sit
 * mid-transaction holding real locks for an external decision, and a
 * coordinator that never itself fails - assumptions that get worse, not
 * better, as more independently-deployed services join the picture.
 * <p>
 * Requires postgres-logistics and postgres-inventory to be running with
 * {@code max_prepared_transactions > 0} (see compose.yaml) - Postgres
 * disables prepared transactions by default, and PREPARE TRANSACTION raises
 * a plain SQLException ("prepared transactions are disabled") without it.
 */
class TwoPhaseCommitLabTest {

    private static final String LOGISTICS_URL = "jdbc:postgresql://localhost:5433/logistics";
    private static final String LOGISTICS_USER = "logistics";
    private static final String LOGISTICS_PASSWORD = "logistics";

    private static final String INVENTORY_URL = "jdbc:postgresql://localhost:5434/inventory";
    private static final String INVENTORY_USER = "inventory";
    private static final String INVENTORY_PASSWORD = "inventory";

    private static final String GID_PREFIX = "twopc-lab-";

    @BeforeEach
    void createLabTable() throws SQLException {
        for (String[] db : databases()) {
            try (Connection connection = connect(db);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS two_pc_lab (id TEXT PRIMARY KEY, note TEXT)");
            }
        }
    }

    @AfterEach
    void cleanUp() throws SQLException {
        for (String[] db : databases()) {
            try (Connection connection = connect(db)) {
                // Safety net: if an earlier run of this class crashed
                // mid-test, its prepared transaction is still sitting on the
                // server holding locks - roll it back so it never blocks a
                // later run indefinitely. This is, in miniature, exactly the
                // "coordinator recovery" step a real TP monitor performs.
                for (String gid : preparedGids(connection)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ROLLBACK PREPARED '" + gid + "'");
                    }
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DELETE FROM two_pc_lab");
                }
            }
        }
    }

    @Test
    void prepareOnBothThenCommitAppliesTheWriteAtomicallyOnBothResourceManagers() throws SQLException {
        String gid = GID_PREFIX + UUID.randomUUID();
        String rowId = UUID.randomUUID().toString();

        // Phase 1 - PREPARE ("vote"): each resource manager independently
        // makes its own write durable and promises it CAN commit, without
        // committing yet. The preparing connections are closed right after
        // - Postgres keeps the prepared transaction alive server-side with
        // no client attached, which is the entire point: a separate
        // coordinator, possibly a different process entirely, decides later.
        try (Connection logistics = connect(LOGISTICS_URL, LOGISTICS_USER, LOGISTICS_PASSWORD)) {
            prepareInsert(logistics, gid, rowId, "order-service side");
        }
        try (Connection inventory = connect(INVENTORY_URL, INVENTORY_USER, INVENTORY_PASSWORD)) {
            prepareInsert(inventory, gid, rowId, "inventory-service side");
        }

        // In-doubt window: both participants voted yes and are now waiting.
        // The write is durable but not yet visible to anyone - this is what
        // "committed to disk but not committed to the world" looks like.
        try (Connection check = connect(LOGISTICS_URL, LOGISTICS_USER, LOGISTICS_PASSWORD)) {
            assertThat(preparedGids(check)).contains(gid);
            assertThat(rowVisible(check, rowId)).isFalse();
        }
        try (Connection check = connect(INVENTORY_URL, INVENTORY_USER, INVENTORY_PASSWORD)) {
            assertThat(preparedGids(check)).contains(gid);
            assertThat(rowVisible(check, rowId)).isFalse();
        }

        // Phase 2 - the coordinator's decision, issued from fresh
        // connections that took no part in phase 1: COMMIT PREPARED does
        // not need the original session, because the decision belongs to
        // the coordinator alone, not to whichever connection happened to
        // prepare the write.
        for (String[] db : databases()) {
            try (Connection connection = connect(db);
                 Statement statement = connection.createStatement()) {
                statement.execute("COMMIT PREPARED '" + gid + "'");
            }
        }

        try (Connection check = connect(LOGISTICS_URL, LOGISTICS_USER, LOGISTICS_PASSWORD)) {
            assertThat(preparedGids(check)).doesNotContain(gid);
            assertThat(rowVisible(check, rowId)).isTrue();
        }
        try (Connection check = connect(INVENTORY_URL, INVENTORY_USER, INVENTORY_PASSWORD)) {
            assertThat(preparedGids(check)).doesNotContain(gid);
            assertThat(rowVisible(check, rowId)).isTrue();
        }
    }

    @Test
    void oneParticipantFailingToPrepareForcesTheCoordinatorToAbortTheOther() throws SQLException {
        String gid = GID_PREFIX + UUID.randomUUID();
        String rowId = UUID.randomUUID().toString();

        // logistics prepares fine and is now sitting in-doubt, waiting.
        try (Connection logistics = connect(LOGISTICS_URL, LOGISTICS_USER, LOGISTICS_PASSWORD)) {
            prepareInsert(logistics, gid, rowId, "order-service side");
        }

        // inventory "votes no": its own write fails before it ever reaches
        // PREPARE TRANSACTION - standing in for a constraint violation or
        // any other reason a real participant can't honor its half.
        try (Connection inventory = connect(INVENTORY_URL, INVENTORY_USER, INVENTORY_PASSWORD)) {
            inventory.setAutoCommit(false);
            try (Statement statement = inventory.createStatement()) {
                statement.execute(
                        "INSERT INTO two_pc_lab (id, note) VALUES ('" + rowId + "', 'inventory-service side')");
                assertThatThrownBy(() -> statement.execute(
                        "INSERT INTO two_pc_lab (id, note) VALUES ('" + rowId + "', 'duplicate')"))
                        .isInstanceOf(SQLException.class);
            }
            inventory.rollback();
        }

        // Atomicity now depends entirely on the coordinator noticing the
        // failed vote and explicitly aborting the participant that DID
        // prepare. Nothing does this automatically - skip this step and
        // logistics's row commits alone, inventory's never existed: the
        // exact lost-atomicity failure 2PC exists to prevent.
        try (Connection logistics = connect(LOGISTICS_URL, LOGISTICS_USER, LOGISTICS_PASSWORD);
             Statement statement = logistics.createStatement()) {
            statement.execute("ROLLBACK PREPARED '" + gid + "'");
        }

        try (Connection check = connect(LOGISTICS_URL, LOGISTICS_USER, LOGISTICS_PASSWORD)) {
            assertThat(preparedGids(check)).doesNotContain(gid);
            assertThat(rowVisible(check, rowId)).isFalse();
        }
        try (Connection check = connect(INVENTORY_URL, INVENTORY_USER, INVENTORY_PASSWORD)) {
            assertThat(rowVisible(check, rowId)).isFalse();
        }
    }

    @Test
    void coordinatorFailureAfterPrepareLeavesTheParticipantHoldingRealLocksIndefinitely() throws SQLException {
        String gid = GID_PREFIX + UUID.randomUUID();
        String rowId = UUID.randomUUID().toString();

        try {
            // Both prepare, then - standing in for "the coordinator process
            // crashes right here, before telling anyone commit or abort" -
            // nothing decides. Both connections close with no decision made.
            try (Connection logistics = connect(LOGISTICS_URL, LOGISTICS_USER, LOGISTICS_PASSWORD)) {
                prepareInsert(logistics, gid, rowId, "order-service side");
            }
            try (Connection inventory = connect(INVENTORY_URL, INVENTORY_USER, INVENTORY_PASSWORD)) {
                prepareInsert(inventory, gid, rowId, "inventory-service side");
            }

            // The prepared transaction is still parked, unresolved, on both
            // sides - durability in phase 1 means it survives every client
            // disconnecting, including the one that created it.
            try (Connection check = connect(LOGISTICS_URL, LOGISTICS_USER, LOGISTICS_PASSWORD)) {
                assertThat(preparedGids(check)).contains(gid);
            }
            try (Connection check = connect(INVENTORY_URL, INVENTORY_USER, INVENTORY_PASSWORD)) {
                assertThat(preparedGids(check)).contains(gid);
            }

            // And it isn't just bookkeeping: the row lock from the prepared
            // insert is real and still held. A second session inserting the
            // same primary key has to wait to find out whether that key
            // will end up taken or free - a decision that, in this
            // scenario, never comes. Proven by making it give up.
            try (Connection blocked = connect(LOGISTICS_URL, LOGISTICS_USER, LOGISTICS_PASSWORD)) {
                blocked.setAutoCommit(false);
                try (Statement setTimeout = blocked.createStatement()) {
                    setTimeout.execute("SET statement_timeout = '500ms'");
                }
                try (Statement insert = blocked.createStatement()) {
                    assertThatThrownBy(() -> insert.execute(
                            "INSERT INTO two_pc_lab (id, note) VALUES ('" + rowId + "', 'conflict-check')"))
                            .isInstanceOf(PSQLException.class)
                            .hasMessageContaining("statement timeout");
                }
                blocked.rollback();
            }
        } finally {
            // Recovery: what a restarted coordinator does on boot is list
            // pg_prepared_xacts and decide for every gid it finds. Here
            // there's no business decision to honor, so: abort.
            for (String[] db : databases()) {
                try (Connection connection = connect(db);
                     Statement statement = connection.createStatement()) {
                    statement.execute("ROLLBACK PREPARED '" + gid + "'");
                }
            }
        }
    }

    private void prepareInsert(Connection connection, String gid, String rowId, String note) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO two_pc_lab (id, note) VALUES ('" + rowId + "', '" + note + "')");
            statement.execute("PREPARE TRANSACTION '" + gid + "'");
        }
    }

    private boolean rowVisible(Connection connection, String rowId) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT 1 FROM two_pc_lab WHERE id = '" + rowId + "'")) {
            return resultSet.next();
        }
    }

    private List<String> preparedGids(Connection connection) throws SQLException {
        List<String> gids = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT gid FROM pg_prepared_xacts WHERE gid LIKE '" + GID_PREFIX + "%'")) {
            while (resultSet.next()) {
                gids.add(resultSet.getString("gid"));
            }
        }
        return gids;
    }

    private List<String[]> databases() {
        return List.of(
                new String[]{LOGISTICS_URL, LOGISTICS_USER, LOGISTICS_PASSWORD},
                new String[]{INVENTORY_URL, INVENTORY_USER, INVENTORY_PASSWORD}
        );
    }

    private Connection connect(String[] db) throws SQLException {
        return connect(db[0], db[1], db[2]);
    }

    private Connection connect(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
