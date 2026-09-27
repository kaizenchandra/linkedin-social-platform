package dev.network.hiring.application;

import static dev.network.hiring.company.CompanyService.*;

import dev.network.hiring.company.*;
import dev.network.hiring.job.*;
import dev.network.hiring.shared.HiringPages;
import dev.network.web.Pages;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class ApplicationService {
  private final ApplicationRepository repo;
  private final CompanyService companies;
  private final JobService jobs;
  private final ProfileSnapshots profiles;
  private final ObjectMapper json;
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final dev.network.hiring.events.EventWriter events;

  public ApplicationService(
      ApplicationRepository repo,
      CompanyService companies,
      JobService jobs,
      ProfileSnapshots profiles,
      ObjectMapper json,
      JdbcTemplate db,
      PlatformTransactionManager tm, dev.network.hiring.events.EventWriter events) {
    this.events = events;
    this.repo = repo;
    this.companies = companies;
    this.jobs = jobs;
    this.profiles = profiles;
    this.json = json;
    this.db = db;
    tx = new TransactionTemplate(tm);
  }

  public record Input(
      @NotBlank String jobId,
      @NotBlank String idempotencyKey,
      @Size(max = 4000) String coverNote) {}

  public record Review(
      @NotNull ApplicationState status, @NotNull @PositiveOrZero Long expectedVersion) {}

  private record RequestContent(String jobId, String coverNote) {}

  public record View(
      String id,
      String companyId,
      String companyName,
      String jobId,
      String applicantId,
      ApplicationState status,
      String coverNote,
      ProfileSnapshots.Snapshot profileSnapshot,
      JobService.View jobSnapshot,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record Summary(
      String id,
      String companyId,
      String companyName,
      String jobId,
      String jobTitle,
      String applicantId,
      ApplicationState status,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record History(
      String id,
      String actorId,
      String fromStatus,
      String toStatus,
      Instant occurredAt,
      long applicationVersion) {}

  private View view(JobApplication a, String actor) {
    boolean redacted = a.state == ApplicationState.WITHDRAWN && !actor.equals(a.applicantId);
    return new View(
        a.id,
        a.companyId,
        a.companyName,
        a.jobId,
        a.applicantId,
        a.state,
        redacted ? null : a.coverNote,
        redacted ? null : json.readValue(a.profileSnapshot, ProfileSnapshots.Snapshot.class),
        json.readValue(a.jobSnapshot, JobService.View.class),
        a.createdAt,
        a.updatedAt,
        a.version);
  }

  private View retry(JobApplication old, String request, String actor) {
    if (!old.requestJson.equals(request))
      throw conflict("Idempotency key reused with different input");
    return view(old, actor);
  }

  public View submit(String actor, Input in) {
    UUID.fromString(in.jobId());
    String key = UUID.fromString(in.idempotencyKey()).toString();
    String request = json.writeValueAsString(new RequestContent(in.jobId(), in.coverNote()));
    var prior = repo.findByApplicantIdAndSubmissionKey(actor, key);
    if (prior.isPresent()) return retry(prior.get(), request, actor);
    if (repo.existsByApplicantIdAndJobId(actor, in.jobId()))
      throw conflict("One application per applicant and job; reapplication is unavailable");
    var profile = profiles.get(actor);
    try {
      return tx.execute(
          s -> {
            var company = companies.lock(jobs.companyId(in.jobId()));
            var job = jobs.lock(in.jobId());
            var concurrent = repo.findByApplicantIdAndSubmissionKey(actor, key);
            if (concurrent.isPresent()) return retry(concurrent.get(), request, actor);
            if (companies.member(company.id, actor))
              throw conflict("Current company members cannot apply to their own company");
            if (!jobs.open(job)) throw conflict("Job is not accepting applications");
            if (repo.existsByApplicantIdAndJobId(actor, job.id))
              throw conflict("One application per applicant and job; reapplication is unavailable");
            var a = new JobApplication();
            a.id = UUID.randomUUID().toString();
            a.companyId = company.id;
            a.companyName = company.displayName;
            a.jobId = job.id;
            a.applicantId = actor;
            a.submissionKey = key;
            a.requestJson = request;
            a.coverNote = in.coverNote();
            a.profileSnapshot = json.writeValueAsString(profile);
            a.jobSnapshot = json.writeValueAsString(jobs.view(job));
            a.createdAt = companies.now();
            a.updatedAt = a.createdAt;
            a = repo.saveAndFlush(a);
            history(a, actor, null);
            events.company("hiring.application.submitted", a.id, a.version, actor, company.id);
            companies.audit(
                company.id, actor, "APPLICATION_SUBMITTED", a.id, "Application submitted");
            return view(a, actor);
          });
    } catch (DataIntegrityViolationException e) {
      var winner = repo.findByApplicantIdAndSubmissionKey(actor, key);
      if (winner.isPresent()) return retry(winner.get(), request, actor);
      throw conflict("One application per applicant and job; concurrent submission conflict");
    }
  }

  private JobApplication authorized(String actor, String id) {
    var companyIds =
        db.queryForList("SELECT company_id FROM job_applications WHERE id=?", String.class, id);
    if (companyIds.isEmpty()) throw missing();
    var company = companies.lock(companyIds.getFirst());
    var a = repo.lock(id).orElseThrow(CompanyService::missing);
    if (!actor.equals(a.applicantId)) companies.requireMember(company, actor);
    return a;
  }

  @Transactional
  public View get(String actor, String id) {
    return view(authorized(actor, id), actor);
  }

  private void history(JobApplication a, String actor, ApplicationState from) {
    db.update(
        "INSERT INTO"
            + " application_history(id,application_id,actor_id,from_state,to_state,occurred_at,application_version)"
            + " VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        a.id,
        actor,
        from == null ? null : from.name(),
        a.state.name(),
        Timestamp.from(companies.now()),
        a.version);
  }

  @Transactional
  public View review(String actor, String id, Review in) {
    var a = authorized(actor, id);
    if (!companies.member(a.companyId, actor)) throw missing();
    if (in.status() == ApplicationState.WITHDRAWN) throw conflict("Only applicants may withdraw");
    if (a.state == in.status()) return view(a, actor);
    if (a.version != in.expectedVersion()) throw conflict("Stale application version");
    if (!a.state.reviewerMayMoveTo(in.status())) throw conflict("Invalid application transition");
    var old = a.state;
    a.state = in.status();
    a.updatedAt = companies.now();
    repo.flush();
    history(a, actor, old);
    events.write("hiring.application.status", a.id, a.version, actor, a.applicantId);
    companies.audit(
        a.companyId, actor, "APPLICATION_STATUS", id, "Application status changed to " + a.state);
    return view(a, actor);
  }

  @Transactional
  public View withdraw(String actor, String id) {
    var a = authorized(actor, id);
    if (!a.applicantId.equals(actor)) throw missing();
    if (a.state == ApplicationState.WITHDRAWN) return view(a, actor);
    if (a.state.terminal()) throw conflict("Terminal applications cannot be withdrawn");
    var old = a.state;
    a.state = ApplicationState.WITHDRAWN;
    a.updatedAt = companies.now();
    repo.flush();
    history(a, actor, old);
    companies.audit(
        a.companyId,
        actor,
        "APPLICATION_WITHDRAWN",
        id,
        "Applicant withdrew; reviewer details redacted");
    return view(a, actor);
  }

  @Transactional
  public List<History> history(String actor, String id) {
    authorized(actor, id);
    return db.query(
        "SELECT * FROM application_history WHERE application_id=? ORDER BY application_version",
        (r, n) ->
            new History(
                r.getString("id"),
                r.getString("actor_id"),
                r.getString("from_state"),
                r.getString("to_state"),
                r.getTimestamp("occurred_at").toInstant(),
                r.getLong("application_version")),
        id);
  }

  @Transactional
  public HiringPages.Slice<Summary> list(
      String actor, String company, String job, ApplicationState status, String cursor, int size) {
    Pages.size(size);
    var args = new ArrayList<Object>();
    String where;
    if (company != null) {
      var c = companies.lock(company);
      companies.requireMember(c, actor);
      where = "company_id=?";
      args.add(company);
    } else {
      where = "applicant_id=?";
      args.add(actor);
    }
    if (job != null) {
      UUID.fromString(job);
      where += " AND job_id=?";
      args.add(job);
    }
    if (status != null) {
      where += " AND state=?";
      args.add(status.name());
    }
    where += HiringPages.after(cursor, args, "created_at");
    args.add(size + 1);
    var rows =
        db.query(
            "SELECT"
                + " id,company_id,company_name,job_id,applicant_id,state,created_at,updated_at,version,JSON_VALUE(job_snapshot,'$.title'"
                + " RETURNING VARCHAR2(160)) job_title FROM job_applications WHERE "
                + where
                + " ORDER BY created_at DESC,id DESC FETCH FIRST ? ROWS ONLY",
            (r, n) ->
                new Summary(
                    r.getString("id"),
                    r.getString("company_id"),
                    r.getString("company_name"),
                    r.getString("job_id"),
                    r.getString("job_title"),
                    r.getString("applicant_id"),
                    ApplicationState.valueOf(r.getString("state")),
                    r.getTimestamp("created_at").toInstant(),
                    r.getTimestamp("updated_at").toInstant(),
                    r.getLong("version")),
            args.toArray());
    return HiringPages.slice(rows, size, v -> HiringPages.cursor(v.createdAt(), v.id()));
  }
}
