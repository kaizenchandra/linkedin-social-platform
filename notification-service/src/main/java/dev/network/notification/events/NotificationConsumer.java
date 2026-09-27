package dev.network.notification.events;

import dev.network.notification.inbox.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;

import java.time.*;
import java.util.*;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationConsumer {
    private final EntityManager em;
    private final NotificationRepository notifications;
    private final ObjectMapper json;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final dev.network.web.ServiceHttp http;
    private final String hiringUrl;
    private final String messagingUrl;
    private final dev.network.web.stream.DurableStream stream;
    private final dev.network.notification.preferences.JobAlertPreferences preferences;

    public NotificationConsumer(
            EntityManager em,
            dev.network.web.stream.DurableStream stream,
            @org.springframework.beans.factory.annotation.Value("${MESSAGING_URL:http://localhost:8085}")
            String messagingUrl,
            NotificationRepository n,
            ObjectMapper json,
            MeterRegistry metrics,
            Clock clock,
            dev.network.web.ServiceHttp http,
            @org.springframework.beans.factory.annotation.Value("${HIRING_URL:http://localhost:8086}")
            String hiringUrl,
            dev.network.notification.preferences.JobAlertPreferences preferences) {
        this.stream = stream;
        this.messagingUrl = messagingUrl;
        this.preferences = preferences;
        this.http = http;
        this.hiringUrl = hiringUrl;
        this.em = em;
        notifications = n;
        this.json = json;
        this.metrics = metrics;
        this.clock = clock;
    }

    @KafkaListener(topics = "network.events.v1")
    @Transactional
    public void consume(String message) {
        if (message.length() > 16384) throw new IllegalArgumentException("Event too large");
        var e = json.readTree(message);
        String id = e.path("eventId").asText(),
                type = e.path("eventType").asText(),
                resource = e.path("aggregateId").asText(),
                actor = e.path("payload").path("actorId").asText(),
                recipient = e.path("payload").path("recipientId").asText();
        for (String uuid : List.of(id, resource, actor)) UUID.fromString(uuid);
        if (e.path("schemaVersion").asInt() != 1
                || !Set.of(
                        "connection.requested",
                        "connection.accepted",
                        "post.liked",
                        "post.commented",
                        "message.sent",
                        "moderation.hidden",
                        "moderation.restored",
                        "hiring.invitation.created",
                        "hiring.application.submitted",
                        "hiring.application.status",
                        "hiring.job.published",
                        "hiring.job.alert")
                .contains(type)) throw new IllegalArgumentException("Unsupported event schema/type");
        String expected =
                type.startsWith("hiring.")
                        ? "hiring-service"
                        : type.startsWith("connection.")
                        ? "member-service"
                        : type.equals("message.sent") ? "messaging-service" : "content-service";
        if (!expected.equals(e.path("producer").asText())
                || !e.has("correlationId")
                || !e.has("causationId")
                || e.path("aggregateVersion").asLong(-1) < 0)
            throw new IllegalArgumentException("Invalid envelope");
        Instant occurred = Instant.parse(e.path("occurredAt").asText());
        Number seen =
                (Number)
                        em.createNativeQuery("SELECT COUNT(*) FROM consumed_events WHERE event_id=:id")
                                .setParameter("id", id)
                                .getSingleResult();
        if (seen.longValue() > 0) {
            metrics.counter("notifications.duplicates").increment();
            return;
        }
        em.createNativeQuery("INSERT INTO consumed_events(event_id,consumed_at) VALUES(:id,:at)")
                .setParameter("id", id)
                .setParameter("at", clock.instant())
                .executeUpdate();
        if (type.equals("hiring.job.published")) return;
        if (type.equals("hiring.job.alert")) {
            UUID.fromString(recipient);
            if (!preferences.authorizeDelivery(
                    recipient, e.path("payload").path("matchId").asText(), resource)) {
                metrics.counter("notifications.job.alerts", "outcome", "suppressed").increment();
                return;
            }
            metrics
                    .timer("notifications.job.alert.latency")
                    .record(Duration.between(occurred, clock.instant()).abs());
        }
        if (type.equals("message.sent")) {
            UUID.fromString(recipient);
            MessageEligibility eligibility;
            try {
                eligibility =
                        http.post(
                                messagingUrl + "/internal/v1/messaging/notification-eligibility",
                                Map.of("memberId", recipient, "conversationId", resource),
                                MessageEligibility.class);
                if (eligibility == null)
                    throw new IllegalStateException("Messaging preference authority unavailable");
            } catch (RuntimeException failure) {
                metrics.counter("notifications.message.preference.failures").increment();
                throw failure;
            }
            if (!eligibility.allowed()) {
                metrics.counter("notifications.message.suppressed").increment();
                return;
            }
        }
        List<String> recipients;
        if (type.equals("hiring.application.submitted")) {
            String company = e.path("payload").path("companyId").asText();
            UUID.fromString(company);
            var resolved =
                    http.post(
                            hiringUrl + "/internal/v1/hiring/recipients",
                            Map.of("companyId", company),
                            Recipients.class);
            if (resolved == null
                    || resolved.memberIds() == null
                    || resolved.memberIds().isEmpty()
                    || resolved.memberIds().size() > 100)
                throw new IllegalStateException("Invalid hiring recipient resolution");
            recipients = resolved.memberIds();
        } else recipients = List.of(recipient);
        var updates = new ArrayList<dev.network.web.stream.DurableStream.Update>();
        for (String target : new TreeSet<>(recipients)) {
            UUID.fromString(target);
            if (!actor.equals(target)
                    || type.startsWith("moderation.")
                    || type.equals("hiring.job.alert")) {
                String notificationId = UUID.randomUUID().toString();
                notifications.saveAndFlush(
                        new Notification(notificationId, id, target, actor, resource, type, occurred));
                updates.add(
                        new dev.network.web.stream.DurableStream.Update(
                                target, "notification.created", notificationId, 0));
            }
        }
        stream.append(updates);
        metrics.counter("notifications.processed").increment();
    }

    public record MessageEligibility(boolean allowed) {
    }

    public record Recipients(List<String> memberIds) {
    }
}
