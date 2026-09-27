package dev.network.hiring.events;

import jakarta.persistence.EntityManager;

import java.time.Clock;
import java.util.*;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventWriter {
    private final EntityManager em;
    private final ObjectMapper json;
    private final Clock clock;

    public EventWriter(EntityManager em, ObjectMapper json, Clock clock) {
        this.em = em;
        this.json = json;
        this.clock = clock;
    }

    public void write(String type, String aggregate, long version, String actor, String recipient) {
        if (actor.equals(recipient)) return;
        write(type, aggregate, version, Map.of("actorId", actor, "recipientId", recipient));
    }

    public void company(String type, String aggregate, long version, String actor, String company) {
        write(type, aggregate, version, Map.of("actorId", actor, "companyId", company));
    }

    public void write(String type, String aggregate, long version, Map<String, String> payload) {
        String id = UUID.randomUUID().toString();
        var row = new Outbox();
        row.id = id;
        row.aggregateId = aggregate;
        row.createdAt = clock.instant();
        var request =
                RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
                        ? a.getRequest()
                        : null;
        String correlation = request == null ? null : request.getHeader("X-Correlation-Id");
        var span = io.opentelemetry.api.trace.Span.current().getSpanContext();
        row.traceParent =
                span.isValid()
                        ? "00-"
                        + span.getTraceId()
                        + "-"
                        + span.getSpanId()
                        + "-"
                        + span.getTraceFlags().asHex()
                        : null;
        if (correlation == null || correlation.length() > 100) correlation = id;
        row.envelope =
                json.writeValueAsString(
                        Map.of(
                                "eventId",
                                id,
                                "eventType",
                                type,
                                "schemaVersion",
                                1,
                                "occurredAt",
                                row.createdAt.toString(),
                                "aggregateId",
                                aggregate,
                                "aggregateVersion",
                                version,
                                "producer",
                                "hiring-service",
                                "correlationId",
                                correlation,
                                "causationId",
                                correlation,
                                "payload",
                                payload));
        em.persist(row);
    }
}
