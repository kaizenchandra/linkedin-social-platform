package dev.network.hiring.alerts;

import dev.network.hiring.company.CompanyService;
import dev.network.hiring.events.EventWriter;
import dev.network.hiring.job.JobService;
import dev.network.hiring.shared.MemberLocks;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertMatcher {
  private final JdbcTemplate db;
  private final MemberLocks locks;
  private final SavedSearchService searches;
  private final CompanyService companies;
  private final JobService jobs;
  private final EventWriter events;
  private final MeterRegistry metrics;

  public AlertMatcher(
      JdbcTemplate db,
      MemberLocks locks,
      SavedSearchService searches,
      CompanyService companies,
      JobService jobs,
      EventWriter events,
      MeterRegistry metrics) {
    this.db = db;
    this.locks = locks;
    this.searches = searches;
    this.companies = companies;
    this.jobs = jobs;
    this.events = events;
    this.metrics = metrics;
  }

  private void committedOutcome(String outcome) {
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                metrics.counter("network.alert.matches", "outcome", outcome).increment();
              }
            });
  }

  public List<String> due() {
    if (db.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE delivered_at IS NULL AND ROWNUM<=1000", Long.class)
        >= 1000) {
      metrics.counter("network.alert.backpressure").increment();
      return List.of();
    }
    return db.queryForList(
        "SELECT job_id FROM alert_work WHERE state IN('PENDING','RETRY') AND (next_attempt_at IS"
            + " NULL OR next_attempt_at<=SYSTIMESTAMP) ORDER BY updated_at,job_id FETCH FIRST 25"
            + " ROWS ONLY",
        String.class);
  }

  @Transactional
  public boolean step(String jobId) {
    var work =
        db.queryForList(
            "SELECT last_member,trace_parent FROM alert_work WHERE job_id=? AND state"
                + " IN('PENDING','RETRY') AND (next_attempt_at IS NULL OR"
                + " next_attempt_at<=SYSTIMESTAMP) FOR UPDATE SKIP LOCKED",
            jobId);
    if (work.isEmpty()) return false;
    long epoch =
        db.queryForObject("SELECT epoch FROM job_publications WHERE job_id=?", Long.class, jobId);
    String last = (String) work.getFirst().get("LAST_MEMBER");
    var owners =
        db.queryForList(
            "SELECT DISTINCT member_id FROM saved_searches WHERE member_id>? AND enabled=1 AND"
                + " deleted=0 AND criteria_epoch<=? AND activation_epoch<=? ORDER BY member_id"
                + " FETCH FIRST 1 ROWS ONLY",
            String.class,
            last,
            epoch,
            epoch);
    if (owners.isEmpty()) {
      db.update(
          "UPDATE alert_work SET state='DONE',updated_at=? WHERE job_id=?",
          Timestamp.from(companies.now()),
          jobId);
      return true;
    }
    String actor = owners.getFirst();
    locks.lock(actor);
    var candidates =
        searches.list(actor).stream()
            .filter(
                s ->
                    s.alertsEnabled() && s.criteriaEpoch() <= epoch && s.activationEpoch() <= epoch)
            .toList();
    companies.lock(jobs.companyId(jobId));
    var job = jobs.lock(jobId);
    if (!jobs.open(job)) {
      db.update(
          "UPDATE alert_work SET state='DONE',updated_at=? WHERE job_id=?",
          Timestamp.from(companies.now()),
          jobId);
      return true;
    }
    Context context = Context.root();
    String trace = (String) work.getFirst().get("TRACE_PARENT");
    if (trace != null && trace.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) {
      var t = trace.split("-");
      context =
          context.with(
              Span.wrap(
                  SpanContext.createFromRemoteParent(
                      t[1], t[2], TraceFlags.fromHex(t[3], 0), TraceState.getDefault())));
    }
    try (Scope scope = context.makeCurrent()) {
      for (var search : candidates) {
        var args = new ArrayList<Object>();
        args.add(jobId);
        String matching = search.criteria().sql("p", args);
        if (db.queryForObject(
                "SELECT COUNT(*) FROM job_publications p WHERE p.job_id=?" + matching,
                Long.class,
                args.toArray())
            == 0) continue;
        String match = UUID.randomUUID().toString();
        int inserted =
            db.update(
                "INSERT INTO"
                    + " job_alert_matches(id,job_id,member_id,search_id,criteria_version,activation_epoch,created_at)"
                    + " SELECT ?,?,?,?,?,?,? FROM dual WHERE NOT EXISTS(SELECT 1 FROM"
                    + " job_alert_matches WHERE job_id=? AND member_id=?)",
                match,
                jobId,
                actor,
                search.id(),
                search.criteriaVersion(),
                search.activationEpoch(),
                Timestamp.from(companies.now()),
                jobId,
                actor);
        if (inserted == 1) {
          String publisher =
              db.queryForObject(
                  "SELECT actor_id FROM job_publications WHERE job_id=?", String.class, jobId);
          events.write(
              "hiring.job.alert",
              jobId,
              job.version,
              Map.of("actorId", publisher, "recipientId", actor, "matchId", match));
          committedOutcome("created");
        } else committedOutcome("duplicate");
        break;
      }
    }
    db.update(
        "UPDATE alert_work SET"
            + " last_member=?,state='PENDING',attempts=0,next_attempt_at=NULL,last_error=NULL,updated_at=?"
            + " WHERE job_id=?",
        actor,
        Timestamp.from(companies.now()),
        jobId);
    return true;
  }

  @Transactional
  public void failure(String id) {
    var rows =
        db.queryForList(
            "SELECT attempts FROM alert_work WHERE job_id=? AND state IN('PENDING','RETRY') FOR"
                + " UPDATE",
            Integer.class,
            id);
    if (rows.isEmpty()) return;
    int attempt = rows.getFirst() + 1;
    db.update(
        "UPDATE alert_work SET"
            + " attempts=?,state=?,next_attempt_at=?,last_error='MATCH_TRANSACTION_FAILED',updated_at=?"
            + " WHERE job_id=?",
        attempt,
        attempt >= 5 ? "FAILED" : "RETRY",
        Timestamp.from(companies.now().plusSeconds(Math.min(60, 1L << attempt))),
        Timestamp.from(companies.now()),
        id);
    metrics.counter("network.alert.failures").increment();
  }

  @Transactional
  public void replay(String job) {
    UUID.fromString(job);
    db.update(
        "UPDATE alert_work SET"
            + " state='PENDING',attempts=0,next_attempt_at=NULL,last_error=NULL,updated_at=? WHERE"
            + " job_id=? AND state='FAILED'",
        Timestamp.from(companies.now()),
        job);
  }

  @Transactional
  public boolean eligible(String actor, String match, String jobId) {
    UUID.fromString(actor);
    UUID.fromString(match);
    UUID.fromString(jobId);
    locks.lock(actor);
    var rows =
        db.queryForList(
            "SELECT search_id,criteria_version,activation_epoch FROM job_alert_matches WHERE id=?"
                + " AND member_id=? AND job_id=?",
            match,
            actor,
            jobId);
    if (rows.isEmpty()) return false;
    var row = rows.getFirst();
    boolean active =
        searches.list(actor).stream()
            .anyMatch(
                s ->
                    s.id().equals(row.get("SEARCH_ID"))
                        && s.alertsEnabled()
                        && s.criteriaVersion() == ((Number) row.get("CRITERIA_VERSION")).longValue()
                        && s.activationEpoch()
                            == ((Number) row.get("ACTIVATION_EPOCH")).longValue());
    if (!active) return false;
    companies.lock(jobs.companyId(jobId));
    return jobs.open(jobs.lock(jobId));
  }
}
