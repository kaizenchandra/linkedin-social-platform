package dev.network.hiring.alerts;

import dev.network.hiring.company.CompanyService;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class PublicationConsumer {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final CompanyService companies;
  private final MeterRegistry metrics;

  public PublicationConsumer(
      JdbcTemplate db, ObjectMapper json, CompanyService companies, MeterRegistry metrics) {
    this.db = db;
    this.json = json;
    this.companies = companies;
    this.metrics = metrics;
  }

  @KafkaListener(topics = "network.events.v1")
  @Transactional
  public void consume(String message) {
    if (message.length() > 16384) throw new IllegalArgumentException("Event too large");
    var e = json.readTree(message);
    if (!e.path("eventType").asText().equals("hiring.job.published")) return;
    String job = e.path("aggregateId").asText(),
        actor = e.path("payload").path("actorId").asText(),
        company = e.path("payload").path("companyId").asText();
    UUID.fromString(job);
    UUID.fromString(actor);
    UUID.fromString(company);
    UUID.fromString(e.path("eventId").asText());
    Instant.parse(e.path("occurredAt").asText());
    if (e.path("schemaVersion").asInt() != 1
        || !e.path("producer").asText().equals("hiring-service")
        || !e.has("correlationId")
        || !e.has("causationId")
        || e.path("aggregateVersion").asLong(-1) < 0)
      throw new IllegalArgumentException("Invalid publication envelope");
    var rows =
        db.queryForList(
            "SELECT job_id FROM job_publications WHERE job_id=? AND actor_id=? AND company_id=? FOR"
                + " UPDATE",
            String.class,
            job,
            actor,
            company);
    if (rows.isEmpty()) throw new IllegalStateException("Publication snapshot unavailable");
    var span = io.opentelemetry.api.trace.Span.current().getSpanContext();
    String trace =
        span.isValid()
            ? "00-"
                + span.getTraceId()
                + "-"
                + span.getSpanId()
                + "-"
                + span.getTraceFlags().asHex()
            : null;
    var now = Timestamp.from(companies.now());
    int created =
        db.update(
            "INSERT INTO alert_work(job_id,created_at,updated_at,trace_parent) SELECT ?,?,?,? FROM"
                + " dual WHERE NOT EXISTS(SELECT 1 FROM alert_work WHERE job_id=?)",
            job,
            now,
            now,
            trace,
            job);
    metrics
        .counter("network.alert.ingestion", "outcome", created == 1 ? "created" : "duplicate")
        .increment();
  }
}
