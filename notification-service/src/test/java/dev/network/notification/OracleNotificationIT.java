package dev.network.notification;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class OracleNotificationIT {
  static OracleContainer oracle;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String url = System.getenv("TEST_DB_URL");
    if (url == null) {
      oracle = new OracleContainer("gvenzl/oracle-free:23.9-slim-faststart");
      oracle.start();
      r.add("spring.datasource.url", oracle::getJdbcUrl);
      r.add("spring.datasource.username", oracle::getUsername);
      r.add("spring.datasource.password", oracle::getPassword);
    } else {
      r.add("spring.datasource.url", () -> url);
      r.add("spring.datasource.username", () -> System.getenv("TEST_NOTIFICATION_DB_USER"));
      r.add(
          "spring.datasource.password",
          () ->
              System.getenv()
                  .getOrDefault(
                      "TEST_NOTIFICATION_DB_PASSWORD", System.getenv("TEST_DB_PASSWORD")));
    }
  }

  @Autowired dev.network.notification.events.NotificationConsumer consumer;
  @Autowired dev.network.notification.inbox.NotificationRepository notifications;
  @Autowired tools.jackson.databind.ObjectMapper json;
  @Autowired dev.network.notification.inbox.NotificationController inbox;

  @Test
  void duplicateIsAtomicAndSelfActionSuppressed() {
    String id = UUID.randomUUID().toString(),
        actor = UUID.randomUUID().toString(),
        recipient = UUID.randomUUID().toString();
    var event = new HashMap<String, Object>();
    event.put("eventId", id);
    event.put("eventType", "post.liked");
    event.put("schemaVersion", 1);
    event.put("occurredAt", "2026-01-01T00:00:00Z");
    event.put("aggregateId", UUID.randomUUID().toString());
    event.put("aggregateVersion", 0);
    event.put("producer", "content-service");
    event.put("correlationId", id);
    event.put("causationId", id);
    event.put("payload", Map.of("actorId", actor, "recipientId", recipient));
    String message = json.writeValueAsString(event);
    consumer.consume(message);
    consumer.consume(message);
    assertThat(notifications.countByEventId(id)).isEqualTo(1);
    var jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
            .header("alg", "test")
            .subject(recipient)
            .build();
    var note =
        inbox.list(jwt, 0, 100).stream()
            .filter(n -> n.actorId().equals(actor))
            .findFirst()
            .orElseThrow();
    var first = inbox.read(jwt, note.id());
    assertThat(inbox.read(jwt, note.id()).readAt()).isEqualTo(first.readAt());
    String own = UUID.randomUUID().toString();
    event.put("eventId", own);
    event.put("payload", Map.of("actorId", actor, "recipientId", actor));
    consumer.consume(json.writeValueAsString(event));
    assertThat(notifications.countByEventId(own)).isZero();
  }

  @Test
  void invalidEnvelopeCannotCommit() {
    assertThatThrownBy(() -> consumer.consume("{}")).isInstanceOf(IllegalArgumentException.class);
  }
}
