package dev.network.hiring.moderation;

import static dev.network.hiring.company.CompanyService.missing;
import dev.network.hiring.company.CompanyService;
import dev.network.hiring.job.*;
import dev.network.hiring.shared.HiringPages;
import dev.network.web.Pages;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HiringModeration {
 public enum Reason { SPAM, SUSPECTED_FRAUD, INAPPROPRIATE, OTHER }
 public enum Action { HIDE, RESTORE, DISMISS }
 public record Report(String id,String jobId,Reason reason,String explanation,String state,Instant createdAt) {}
 public record Inspection(Report report,JobService.View job) {}
 public record Audit(String id,String actorId,String action,String reason,Instant occurredAt) {}
 private final JdbcTemplate db;
 private final CompanyService companies;
 private final JobService jobs;
 private final JobRepository repository;
 public HiringModeration(JdbcTemplate db,CompanyService companies,JobService jobs,JobRepository repository) {
  this.db=db;this.companies=companies;this.jobs=jobs;this.repository=repository;
 }
 private static final RowMapper<Report> REPORT=(r,n)->new Report(r.getString("id"),r.getString("job_id"),Reason.valueOf(r.getString("reason")),r.getString("explanation"),r.getString("state"),r.getTimestamp("created_at").toInstant());
 private Report report(String id) {
  UUID.fromString(id);var rows=db.query("SELECT * FROM job_reports WHERE id=?",REPORT,id);
  if(rows.isEmpty()) throw missing();return rows.getFirst();
 }
 @Transactional public Report submit(String actor,String jobId,Reason reason,String explanation) {
  if(reason==null || explanation!=null && explanation.length()>1000 || reason==Reason.OTHER && (explanation==null || explanation.isBlank())) throw new IllegalArgumentException("Invalid report");
  companies.lock(jobs.companyId(jobId));var job=jobs.lock(jobId);
  if(!jobs.open(job)) throw missing();
  var old=db.query("SELECT * FROM job_reports WHERE reporter_id=? AND job_id=? AND state='OPEN'",REPORT,actor,jobId);
  if(!old.isEmpty()) return old.getFirst();
  String id=UUID.randomUUID().toString();
  db.update("INSERT INTO job_reports(id,job_id,reporter_id,reason,explanation,state,created_at) VALUES(?,?,?,?,?,'OPEN',?)",id,jobId,actor,reason.name(),explanation,Timestamp.from(companies.now()));
  return report(id);
 }
 @Transactional(readOnly=true) public HiringPages.Slice<Report> own(String actor,String cursor,int size) {return list("reporter_id=?",new ArrayList<>(List.of(actor)),cursor,size);}
 @PreAuthorize("hasRole('moderator')") @Transactional(readOnly=true)
 public HiringPages.Slice<Report> queue(String cursor,int size) {return list("state='OPEN'",new ArrayList<>(),cursor,size);}
 private HiringPages.Slice<Report> list(String where,ArrayList<Object> args,String cursor,int size) {
  Pages.size(size);where+=HiringPages.after(cursor,args,"created_at");args.add(size+1);
  var rows=db.query("SELECT * FROM job_reports WHERE "+where+" ORDER BY created_at DESC,id DESC FETCH FIRST ? ROWS ONLY",REPORT,args.toArray());
  return HiringPages.slice(rows,size,r->HiringPages.cursor(r.createdAt(),r.id()));
 }
 private void audit(String actor,String report,String action,String reason) {
  if(reason==null || reason.isBlank() || reason.length()>1000) throw new IllegalArgumentException("A bounded audit reason is required");
  db.update("INSERT INTO hiring_moderation_audit(id,report_id,actor_id,action,reason,occurred_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),report,actor,action,reason,Timestamp.from(companies.now()));
 }
 @PreAuthorize("hasRole('moderator')") @Transactional
 public Inspection inspect(String actor,String id,String reason) {
  var r=report(id);companies.lock(jobs.companyId(r.jobId()));var j=jobs.lock(r.jobId());
  audit(actor,id,"INSPECT",reason);return new Inspection(r,jobs.view(j));
 }
 @PreAuthorize("hasRole('moderator')") @Transactional
 public Report act(String actor,String id,Action action,String reason) {
  var r=report(id);companies.lock(jobs.companyId(r.jobId()));var j=jobs.lock(r.jobId());
  if(action==null) throw new IllegalArgumentException("Action required");
  if(action==Action.DISMISS) db.update("UPDATE job_reports SET state='DISMISSED' WHERE id=?",id);
  else {j.hidden=action==Action.HIDE;j.updatedAt=companies.now();repository.flush();}
  audit(actor,id,action.name(),reason);return report(id);
 }
 @PreAuthorize("hasRole('moderator')") @Transactional(readOnly=true)
 public List<Audit> history(String id,int page,int size) {
  report(id);return db.query("SELECT * FROM hiring_moderation_audit WHERE report_id=? ORDER BY occurred_at DESC,id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(r,n)->new Audit(r.getString("id"),r.getString("actor_id"),r.getString("action"),r.getString("reason"),r.getTimestamp("occurred_at").toInstant()),id,Pages.page(page)*Pages.size(size),size);
 }
}
