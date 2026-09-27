package dev.network.notification.preferences;

import dev.network.web.ServiceHttp;

import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobAlertPreferences {
    private final JdbcTemplate db;
    private final ServiceHttp http;
    private final String hiring;

    public JobAlertPreferences(
            JdbcTemplate db,
            ServiceHttp http,
            @Value("${HIRING_URL:http://localhost:8086}") String hiring) {
        this.db = db;
        this.http = http;
        this.hiring = hiring;
    }

    private void lock(String actor) {
        try {
            db.update(
                    "INSERT INTO job_alert_preferences(member_id,enabled) SELECT ?,1 FROM dual WHERE NOT"
                            + " EXISTS(SELECT 1 FROM job_alert_preferences WHERE member_id=?)",
                    actor,
                    actor);
        } catch (DuplicateKeyException concurrentInitialization) {
        }
        db.queryForObject(
                "SELECT enabled FROM job_alert_preferences WHERE member_id=? FOR UPDATE",
                Integer.class,
                actor);
    }

    @Transactional
    public void set(String actor, boolean enabled) {
        lock(actor);
        db.update(
                "UPDATE job_alert_preferences SET enabled=? WHERE member_id=?", enabled ? 1 : 0, actor);
    }

    @Transactional(readOnly = true)
    public View get(String actor) {
        boolean global =
                db
                        .queryForList(
                                "SELECT enabled FROM job_alert_preferences WHERE member_id=?",
                                Integer.class,
                                actor)
                        .stream()
                        .findFirst()
                        .orElse(1)
                        == 1;
        Boolean consent =
                http.post(
                        hiring + "/internal/v1/hiring/alerts/consent",
                        Map.of("memberId", actor),
                        Boolean.class);
        if (consent == null) throw new IllegalStateException("Alert consent unavailable");
        return new View(global && consent, global, consent);
    }

    /**
     * Runs in notification consumer transaction, serializing local disable against persistence.
     */
    public boolean authorizeDelivery(String actor, String match, String job) {
        UUID.fromString(match);
        lock(actor);
        if (db.queryForObject(
                "SELECT enabled FROM job_alert_preferences WHERE member_id=?", Integer.class, actor)
                == 0) return false;
        Boolean allowed =
                http.post(
                        hiring + "/internal/v1/hiring/alerts/eligible",
                        Map.of("memberId", actor, "matchId", match, "jobId", job),
                        Boolean.class);
        if (allowed == null) throw new IllegalStateException("Alert eligibility unavailable");
        if (!allowed) return false;
        return db.update(
                "INSERT INTO job_alert_deliveries(member_id,job_id,match_id) SELECT ?,?,? FROM dual"
                        + " WHERE NOT EXISTS(SELECT 1 FROM job_alert_deliveries WHERE member_id=? AND"
                        + " job_id=?)",
                actor,
                job,
                match,
                actor,
                job)
                == 1;
    }

    public record View(boolean enabled, boolean globallyEnabled, boolean hasActiveSearch) {
    }
}
