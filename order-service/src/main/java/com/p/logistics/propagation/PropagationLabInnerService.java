package com.p.logistics.propagation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// A separate bean from PropagationLabService on purpose: Spring's
// @Transactional is proxy-based AOP, so it only takes effect on a call that
// actually goes through the bean's proxy - i.e. a call from a DIFFERENT
// bean. Same reason InventoryService.reserve() calls a distinct
// InventoryTransactionService bean rather than an internal method.
@Service
public class PropagationLabInnerService {

    private final JdbcTemplate jdbcTemplate;

    public PropagationLabInnerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertInRequiresNew(String id, String note) {
        jdbcTemplate.update("INSERT INTO propagation_lab (id, note) VALUES (?, ?)", id, note);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void insertInRequired(String id, String note) {
        jdbcTemplate.update("INSERT INTO propagation_lab (id, note) VALUES (?, ?)", id, note);
    }
}
