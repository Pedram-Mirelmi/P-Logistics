package com.p.logistics.propagation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 06 transaction-composability exercise: three ways of nesting a
 * second write inside an outer, already-transactional method, and what
 * survives when the outer method fails after the inner call returns.
 * PropagationLabExperimentTest is what actually proves the outcomes named
 * in each method's comment - this class only sets them up.
 */
@Service
public class PropagationLabService {

    private final JdbcTemplate jdbcTemplate;
    private final PropagationLabInnerService innerService;

    public PropagationLabService(JdbcTemplate jdbcTemplate, PropagationLabInnerService innerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.innerService = innerService;
    }

    // REQUIRES_NEW, called on a separate bean: the inner call suspends this
    // transaction, runs in and commits its own, independent one, then this
    // transaction resumes. The forced failure below rolls back only the
    // outer row - the inner row is already durably committed by the time
    // this exception is even thrown.
    @Transactional(propagation = Propagation.REQUIRED)
    public void outerFailsAfterInnerRequiresNew(String outerId, String innerId) {
        jdbcTemplate.update("INSERT INTO propagation_lab (id, note) VALUES (?, ?)", outerId, "outer");
        innerService.insertInRequiresNew(innerId, "inner-requires-new");
        throw new RuntimeException("forced failure after inner REQUIRES_NEW already committed");
    }

    // REQUIRED, called on a separate bean: no ambient transaction yet on
    // that bean's call, so it joins THIS one (REQUIRED's whole point) rather
    // than starting a new one. One physical transaction, one outcome - the
    // forced failure rolls both rows back together.
    @Transactional(propagation = Propagation.REQUIRED)
    public void outerFailsAfterInnerRequired(String outerId, String innerId) {
        jdbcTemplate.update("INSERT INTO propagation_lab (id, note) VALUES (?, ?)", outerId, "outer");
        innerService.insertInRequired(innerId, "inner-required");
        throw new RuntimeException("forced failure - should roll back both rows");
    }

    // The self-invocation trap: insertInRequiresNewSelf() below IS annotated
    // REQUIRES_NEW, but it's called via a plain "this." call from inside
    // THIS SAME class/bean. Spring's @Transactional is proxy-based AOP - a
    // call that never goes back out through the proxy is invisible to it,
    // so the annotation is silently ignored and the call just runs as part
    // of whatever transaction is already active. Contrast this method's
    // outcome with outerFailsAfterInnerRequiresNew() above: same annotation,
    // opposite result, purely because of which bean makes the call.
    @Transactional(propagation = Propagation.REQUIRED)
    public void outerFailsAfterSelfInvokedRequiresNew(String outerId, String innerId) {
        jdbcTemplate.update("INSERT INTO propagation_lab (id, note) VALUES (?, ?)", outerId, "outer-self-invocation-demo");
        this.insertInRequiresNewSelf(innerId, "self-invoked-requires-new");
        throw new RuntimeException("forced failure - self-invoked REQUIRES_NEW does NOT survive this");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertInRequiresNewSelf(String id, String note) {
        jdbcTemplate.update("INSERT INTO propagation_lab (id, note) VALUES (?, ?)", id, note);
    }
}
