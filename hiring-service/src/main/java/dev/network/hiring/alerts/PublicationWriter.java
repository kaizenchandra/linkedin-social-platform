package dev.network.hiring.alerts;

import dev.network.hiring.events.EventWriter;
import dev.network.hiring.job.Job;

import java.sql.Timestamp;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PublicationWriter {
    private final JdbcTemplate db;
    private final EligibilityEpoch epoch;
    private final EventWriter events;

    public PublicationWriter(JdbcTemplate db, EligibilityEpoch epoch, EventWriter events) {
        this.db = db;
        this.epoch = epoch;
        this.events = events;
    }

    public void publish(Job job, String actor) {
        db.update(
                "INSERT INTO"
                        + " job_publications(job_id,company_id,actor_id,epoch,published_at,title,description,location,work_arrangement,employment_type)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                job.id,
                job.companyId,
                actor,
                epoch.next(),
                Timestamp.from(job.publishedAt),
                job.title,
                job.description,
                job.location,
                job.workArrangement.name(),
                job.employmentType.name());
        events.write(
                "hiring.job.published",
                job.id,
                job.version,
                Map.of("actorId", actor, "companyId", job.companyId));
    }
}
