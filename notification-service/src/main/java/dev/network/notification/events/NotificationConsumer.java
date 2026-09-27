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

  public NotificationConsumer(
      EntityManager em,
      NotificationRepository n,
      ObjectMapper json,
      MeterRegistry metrics,
      Clock clock) {
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
    for (String uuid : List.of(id, resource, actor, recipient)) UUID.fromString(uuid);
    if (e.path("schemaVersion").asInt() != 1
        || !Set.of(
                "connection.requested",
                "connection.accepted",
                "post.liked",
                "post.commented",
                "message.sent",
                "moderation.hidden",
                "moderation.restored")
            .contains(type)) throw new IllegalArgumentException("Unsupported event schema/type");
    String expected =
        type.startsWith("connection.")
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
    if (!actor.equals(recipient) || type.startsWith("moderation."))
      notifications.saveAndFlush(
          new Notification(
              UUID.randomUUID().toString(), id, recipient, actor, resource, type, occurred));
    metrics.counter("notifications.processed").increment();
  }
}
