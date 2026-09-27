package dev.network.hiring.job;

import static dev.network.hiring.company.CompanyService.*;

import dev.network.hiring.company.*;
import dev.network.hiring.shared.HiringPages;
import dev.network.web.Pages;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {
  private final JobRepository repo;
  private final CompanyService companies;
  private final JdbcTemplate db;

  public JobService(JobRepository repo, CompanyService companies, JdbcTemplate db) {
    this.repo = repo;
    this.companies = companies;
    this.db = db;
  }

  public record Input(
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 16000) String description,
      @NotBlank @Size(max = 150) String location,
      @NotNull Job.Work workArrangement,
      @NotNull Job.Employment employmentType,
      @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal salaryMinimum,
      @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal salaryMaximum,
      @Pattern(regexp = "[A-Z]{3}") String salaryCurrency,
      Job.Period payPeriod,
      Instant deadline,
      @PositiveOrZero Long expectedVersion) {}

  public record View(
      String id,
      String companyId,
      String title,
      String description,
      String location,
      Job.Work workArrangement,
      Job.Employment employmentType,
      BigDecimal salaryMinimum,
      BigDecimal salaryMaximum,
      String salaryCurrency,
      Job.Period payPeriod,
      Instant deadline,
      Job.State state,
      boolean hidden,
      Instant createdAt,
      Instant updatedAt,
      Instant publishedAt,
      long version) {}

  public View view(Job j) {
    return new View(
        j.id,
        j.companyId,
        j.title,
        j.description,
        j.location,
        j.workArrangement,
        j.employmentType,
        j.salaryMinimum,
        j.salaryMaximum,
        j.salaryCurrency,
        j.payPeriod,
        j.deadline,
        j.state,
        j.hidden,
        j.createdAt,
        j.updatedAt,
        j.publishedAt,
        j.version);
  }

  public boolean open(Job j) {
    return j.state == Job.State.PUBLISHED
        && !j.hidden
        && (j.deadline == null || companies.now().isBefore(j.deadline));
  }

  public Job lock(String id) {
    return repo.lock(id).orElseThrow(CompanyService::missing);
  }

  public String companyId(String id) {
    return db.queryForList("SELECT company_id FROM jobs WHERE id=?", String.class, id).stream()
        .findFirst()
        .orElseThrow(CompanyService::missing);
  }

  private void apply(Job j, Input in) {
    boolean amounts = in.salaryMinimum() != null || in.salaryMaximum() != null;
    if (amounts) {
      if (in.salaryCurrency() == null || in.payPeriod() == null)
        throw new IllegalArgumentException("Salary currency and pay period required");
      Currency.getInstance(in.salaryCurrency());
    } else if (in.salaryCurrency() != null || in.payPeriod() != null)
      throw new IllegalArgumentException("Salary amount required");
    if (in.salaryMinimum() != null
        && in.salaryMaximum() != null
        && in.salaryMinimum().compareTo(in.salaryMaximum()) > 0)
      throw new IllegalArgumentException("Salary minimum exceeds maximum");
    if (in.deadline() != null && !companies.now().isBefore(in.deadline()))
      throw new IllegalArgumentException("Deadline must be in the future");
    j.title = in.title().strip();
    j.description = in.description();
    j.location = in.location().strip();
    j.workArrangement = in.workArrangement();
    j.employmentType = in.employmentType();
    j.salaryMinimum = in.salaryMinimum();
    j.salaryMaximum = in.salaryMaximum();
    j.salaryCurrency = in.salaryCurrency();
    j.payPeriod = in.payPeriod();
    j.deadline = in.deadline();
    j.updatedAt = companies.now();
  }

  @Transactional
  public View create(String actor, String company, Input in) {
    var c = companies.lock(company);
    companies.requireMember(c, actor);
    var j = new Job();
    j.id = UUID.randomUUID().toString();
    j.companyId = company;
    j.createdAt = companies.now();
    apply(j, in);
    repo.saveAndFlush(j);
    companies.audit(company, actor, "JOB_CREATED", j.id, "Draft job created");
    return view(j);
  }

  @Transactional
  public View edit(String actor, String id, Input in) {
    var c = companies.lock(companyId(id));
    companies.requireMember(c, actor);
    var j = lock(id);
    if (j.state == Job.State.CLOSED) throw conflict("Closed jobs cannot be edited");
    if (in.expectedVersion() == null || in.expectedVersion() != j.version)
      throw conflict("Stale job version");
    apply(j, in);
    repo.flush();
    companies.audit(c.id, actor, "JOB_EDITED", id, "Job details edited");
    return view(j);
  }

  @Transactional
  public View transition(String actor, String id, String action) {
    var c = companies.lock(companyId(id));
    companies.requireMember(c, actor);
    var j = lock(id);
    if (action.equals("publish")) {
      if (j.state == Job.State.PUBLISHED) return view(j);
      if (j.state != Job.State.DRAFT) throw conflict("Closed jobs cannot reopen");
      if (j.deadline != null && !companies.now().isBefore(j.deadline))
        throw conflict("Deadline passed");
      j.state = Job.State.PUBLISHED;
      j.publishedAt = companies.now();
    } else if (action.equals("close")) {
      if (j.state == Job.State.CLOSED) return view(j);
      if (j.state != Job.State.PUBLISHED) throw conflict("Only published jobs may close");
      j.state = Job.State.CLOSED;
    } else throw new IllegalArgumentException("Unknown job action");
    j.updatedAt = companies.now();
    repo.flush();
    companies.audit(c.id, actor, "JOB_" + j.state, id, "Job lifecycle changed");
    return view(j);
  }

  @Transactional(readOnly = true)
  public View get(String id) {
    var j = repo.findById(id).orElseThrow(CompanyService::missing);
    if (!open(j)) throw missing();
    return view(j);
  }

  @Transactional
  public View managed(String actor, String company, String id) {
    var c = companies.lock(company);
    companies.requireMember(c, actor);
    var j = repo.findById(id).orElseThrow(CompanyService::missing);
    if (!j.companyId.equals(company)) throw missing();
    return view(j);
  }

  private Instant at(ResultSet r, String name) throws SQLException {
    var v = r.getTimestamp(name);
    return v == null ? null : v.toInstant();
  }

  private View row(ResultSet r, int n) throws SQLException {
    return new View(
        r.getString("id"),
        r.getString("company_id"),
        r.getString("title"),
        r.getString("description"),
        r.getString("location"),
        Job.Work.valueOf(r.getString("work_arrangement")),
        Job.Employment.valueOf(r.getString("employment_type")),
        r.getBigDecimal("salary_minimum"),
        r.getBigDecimal("salary_maximum"),
        r.getString("salary_currency"),
        r.getString("pay_period") == null ? null : Job.Period.valueOf(r.getString("pay_period")),
        at(r, "deadline"),
        Job.State.valueOf(r.getString("state")),
        r.getInt("hidden") == 1,
        at(r, "created_at"),
        at(r, "updated_at"),
        at(r, "published_at"),
        r.getLong("version"));
  }

  private String term(String value) {
    return "%"
        + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }

  @Transactional(readOnly = true)
  public HiringPages.Slice<View> search(
      String q,
      String company,
      String location,
      Job.Work work,
      Job.Employment employment,
      String cursor,
      int size) {
    Pages.size(size);
    if (q == null) q = "";
    if (q.length() > 100 || location != null && location.length() > 150)
      throw new IllegalArgumentException("Search too long");
    var args = new ArrayList<Object>();
    String where = "state='PUBLISHED' AND hidden=0 AND (deadline IS NULL OR deadline>?)";
    args.add(Timestamp.from(companies.now()));
    if (!q.isBlank()) {
      var terms = q.strip().split("\\s+");
      if (terms.length > 8) throw new IllegalArgumentException("At most8 search terms");
      for (String t : terms) {
        where += " AND (LOWER(title) LIKE ? ESCAPE '!' OR LOWER(description) LIKE ? ESCAPE '!')";
        args.add(term(t));
        args.add(term(t));
      }
    }
    if (company != null) {
      UUID.fromString(company);
      where += " AND company_id=?";
      args.add(company);
    }
    if (location != null && !location.isBlank()) {
      where += " AND LOWER(location) LIKE ? ESCAPE '!'";
      args.add(term(location.strip()));
    }
    if (work != null) {
      where += " AND work_arrangement=?";
      args.add(work.name());
    }
    if (employment != null) {
      where += " AND employment_type=?";
      args.add(employment.name());
    }
    where += HiringPages.after(cursor, args, "published_at");
    args.add(size + 1);
    var rows =
        db.query(
            "SELECT * FROM jobs WHERE "
                + where
                + " ORDER BY published_at DESC,id DESC FETCH FIRST ? ROWS ONLY",
            this::row,
            args.toArray());
    return HiringPages.slice(rows, size, v -> HiringPages.cursor(v.publishedAt(), v.id()));
  }

  @Transactional
  public HiringPages.Slice<View> manage(
      String actor, String company, Job.State state, String cursor, int size) {
    var c = companies.lock(company);
    companies.requireMember(c, actor);
    Pages.size(size);
    var args = new ArrayList<Object>();
    args.add(company);
    String where = "company_id=?";
    if (state != null) {
      where += " AND state=?";
      args.add(state.name());
    }
    where += HiringPages.after(cursor, args, "created_at");
    args.add(size + 1);
    return HiringPages.slice(
        db.query(
            "SELECT * FROM jobs WHERE "
                + where
                + " ORDER BY created_at DESC,id DESC FETCH FIRST ? ROWS ONLY",
            this::row,
            args.toArray()),
        size,
        v -> HiringPages.cursor(v.createdAt(), v.id()));
  }
}
