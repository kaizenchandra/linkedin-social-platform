package dev.network.hiring.discovery;

import dev.network.hiring.company.CompanyService;
import dev.network.hiring.job.*;
import dev.network.hiring.shared.HiringPages;
import dev.network.web.Pages;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedJobService {
    private final JdbcTemplate db;
    private final CompanyService companies;
    private final JobService jobs;

    public SavedJobService(JdbcTemplate db, CompanyService companies, JobService jobs) {
        this.db = db;
        this.companies = companies;
        this.jobs = jobs;
    }

    @Transactional
    public void save(String actor, String id) {
        companies.lock(jobs.companyId(id));
        var job = jobs.lock(id);
        if (!jobs.open(job)) throw CompanyService.missing();
        db.update(
                "INSERT INTO saved_jobs(member_id,job_id,saved_at) SELECT ?,?,? FROM dual WHERE NOT EXISTS"
                        + " (SELECT 1 FROM saved_jobs WHERE member_id=? AND job_id=?)",
                actor,
                id,
                Timestamp.from(companies.now()),
                actor,
                id);
    }

    @Transactional
    public void unsave(String actor, String id) {
        UUID.fromString(id);
        db.update("DELETE FROM saved_jobs WHERE member_id=? AND job_id=?", actor, id);
    }

    @Transactional(readOnly = true)
    public HiringPages.Slice<Saved> list(String actor, String cursor, int size) {
        Pages.size(size);
        var args = new ArrayList<Object>();
        args.add(actor);
        String after = HiringPages.after(cursor, args, "s.saved_at").replace("id<?", "s.job_id<?");
        record Row(Saved value, boolean hidden) {
        }
        var rows =
                db.query(
                        "SELECT j.id,j.company_id,j.title,j.location,j.state,j.deadline,s.saved_at,j.hidden"
                                + " FROM saved_jobs s JOIN jobs j ON j.id=s.job_id WHERE s.member_id=?"
                                + after
                                + " ORDER BY s.saved_at DESC,s.job_id DESC FETCH FIRST 500 ROWS ONLY",
                        (r, n) -> {
                            String status = r.getString(5);
                            if (status.equals("PUBLISHED")
                                    && r.getTimestamp(6) != null
                                    && !companies.now().isBefore(r.getTimestamp(6).toInstant())) status = "EXPIRED";
                            return new Row(
                                    new Saved(
                                            r.getString(1),
                                            r.getString(2),
                                            r.getString(3),
                                            r.getString(4),
                                            status,
                                            r.getTimestamp(7).toInstant()),
                                    r.getInt(8) == 1 || status.equals("DRAFT"));
                        },
                        args.toArray());
        var items = new ArrayList<Saved>();
        String next = null;
        int scanned = 0;
        for (var row : rows) {
            scanned++;
            next = HiringPages.cursor(row.value().savedAt(), row.value().jobId());
            if (!row.hidden()) items.add(row.value());
            if (items.size() == size) break;
        }
        boolean more = scanned < rows.size() || rows.size() == 500;
        return new HiringPages.Slice<>(items, more ? next : null, more);
    }

    public record Saved(
            String jobId,
            String companyId,
            String title,
            String location,
            String status,
            Instant savedAt) {
    }
}
