package dev.network.hiring.alerts;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "network.alerts.enabled", havingValue = "true", matchIfMissing = true)
public class AlertWorker {
  private final AlertMatcher matcher;
  private final JdbcTemplate db;
  private volatile double backlog, oldest, failed;

  public AlertWorker(AlertMatcher matcher, JdbcTemplate db, MeterRegistry metrics) {
    this.matcher = matcher;
    this.db = db;
    metrics.gauge("network.alert.backlog", this, x -> x.backlog);
    metrics.gauge("network.alert.oldest.seconds", this, x -> x.oldest);
    metrics.gauge("network.alert.failed", this, x -> x.failed);
  }

  @Scheduled(fixedDelayString = "${network.alerts.delay:1000}")
  public void tick() {
    int remaining = 25;
    while (remaining > 0) {
      var due = matcher.due();
      if (due.isEmpty()) break;
      for (String id : due) {
        try {
          matcher.step(id);
        } catch (Exception e) {
          matcher.failure(id);
        }
        if (--remaining == 0) break;
      }
    }
  }

  @Scheduled(fixedDelay = 10000)
  public void measure() {
    backlog =
        db.queryForObject(
            "SELECT COUNT(*) FROM alert_work WHERE state IN('PENDING','RETRY')", Long.class);
    failed = db.queryForObject("SELECT COUNT(*) FROM alert_work WHERE state='FAILED'", Long.class);
    oldest =
        db.queryForObject(
            "SELECT NVL(MAX((CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS"
                + " DATE)-CAST(SYS_EXTRACT_UTC(created_at) AS DATE))*86400),0) FROM alert_work"
                + " WHERE state IN('PENDING','RETRY')",
            Double.class);
  }
}
