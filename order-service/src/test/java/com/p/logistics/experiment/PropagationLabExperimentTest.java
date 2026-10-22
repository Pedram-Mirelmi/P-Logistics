package com.p.logistics.experiment;

import com.p.logistics.propagation.PropagationLabService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Experiment H (Step 06's transaction-composability exercise): proves,
 * against a real database, what actually commits and what actually rolls
 * back for REQUIRED vs REQUIRES_NEW - and the self-invocation trap that
 * silently turns REQUIRES_NEW back into REQUIRED. @SpringBootTest is
 * required here (not a plain unit test): the whole point is Spring's real
 * proxy-based transactional advice, which only exists on the real beans.
 */
@SpringBootTest
class PropagationLabExperimentTest {

    @Autowired
    private PropagationLabService propagationLabService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM propagation_lab");
    }

    @Test
    void requiresNewOnASeparateBeanSurvivesTheOuterTransactionRollingBack() {
        String outerId = "outer-" + UUID.randomUUID();
        String innerId = "inner-" + UUID.randomUUID();

        assertThatThrownBy(() -> propagationLabService.outerFailsAfterInnerRequiresNew(outerId, innerId))
                .isInstanceOf(RuntimeException.class);

        assertThat(rowExists(outerId)).isFalse();
        assertThat(rowExists(innerId)).isTrue();
    }

    @Test
    void requiredOnASeparateBeanJoinsTheOuterTransactionAndRollsBackWithIt() {
        String outerId = "outer-" + UUID.randomUUID();
        String innerId = "inner-" + UUID.randomUUID();

        assertThatThrownBy(() -> propagationLabService.outerFailsAfterInnerRequired(outerId, innerId))
                .isInstanceOf(RuntimeException.class);

        assertThat(rowExists(outerId)).isFalse();
        assertThat(rowExists(innerId)).isFalse();
    }

    @Test
    void selfInvokedRequiresNewIsSilentlyIgnoredAndRollsBackWithTheOuter() {
        String outerId = "outer-" + UUID.randomUUID();
        String innerId = "inner-" + UUID.randomUUID();

        assertThatThrownBy(() -> propagationLabService.outerFailsAfterSelfInvokedRequiresNew(outerId, innerId))
                .isInstanceOf(RuntimeException.class);

        // The annotation says REQUIRES_NEW. The outcome says otherwise: this
        // call never went back out through the proxy, so Spring never saw
        // it as a transaction boundary at all - it just ran inside the
        // caller's own transaction and rolled back with it, exactly like
        // the plain REQUIRED case above. Compare directly with
        // requiresNewOnASeparateBeanSurvivesTheOuterTransactionRollingBack:
        // identical annotation, opposite outcome, purely because of which
        // bean made the call.
        assertThat(rowExists(outerId)).isFalse();
        assertThat(rowExists(innerId)).isFalse();
    }

    private boolean rowExists(String id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM propagation_lab WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }
}
