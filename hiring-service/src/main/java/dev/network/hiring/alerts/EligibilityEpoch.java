package dev.network.hiring.alerts;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EligibilityEpoch {
    private final JdbcTemplate db;

    public EligibilityEpoch(JdbcTemplate db) {
        this.db = db;
    }

    /**
     * Caller holds a transaction; row lock serializes the order until commit.
     */
    public long next() {
        long epoch =
                db.queryForObject("SELECT epoch FROM alert_epoch WHERE singleton=1 FOR UPDATE", Long.class)
                        + 1;
        db.update("UPDATE alert_epoch SET epoch=? WHERE singleton=1", epoch);
        return epoch;
    }
}
