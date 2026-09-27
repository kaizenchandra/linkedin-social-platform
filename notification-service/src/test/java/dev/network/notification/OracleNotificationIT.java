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

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  dev.network.web.ServiceHttp http;

  @Autowired org.springframework.jdbc.core.JdbcTemplate db;
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
    assertThat(json.readTree(json.writeValueAsString(note)).has("message")).isFalse();
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

  @Test
  void hiringFanoutRetryAndReplayAreAtomic() {
    String eventId = UUID.randomUUID().toString(), actor = UUID.randomUUID().toString();
    String owner = UUID.randomUUID().toString(), recruiter = UUID.randomUUID().toString();
    var event =
        Map.of(
            "eventId",
            eventId,
            "eventType",
            "hiring.application.submitted",
            "schemaVersion",
            1,
            "occurredAt",
            "2026-09-27T00:00:00Z",
            "aggregateId",
            UUID.randomUUID().toString(),
            "aggregateVersion",
            0,
            "producer",
            "hiring-service",
            "correlationId",
            eventId,
            "causationId",
            eventId,
            "payload",
            Map.of("actorId", actor, "companyId", UUID.randomUUID().toString()));
    String message = json.writeValueAsString(event);
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(
                    dev.network.notification.events.NotificationConsumer.Recipients.class)))
        .thenThrow(new IllegalStateException("Recipient service unavailable"));
    assertThatThrownBy(() -> consumer.consume(message)).isInstanceOf(IllegalStateException.class);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM consumed_events WHERE event_id=?", Long.class, eventId))
        .isZero();
    assertThat(notifications.countByEventId(eventId)).isZero();
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(
                    dev.network.notification.events.NotificationConsumer.Recipients.class)))
        .thenReturn(
            new dev.network.notification.events.NotificationConsumer.Recipients(
                List.of(owner, recruiter, actor, recruiter)));
    consumer.consume(message);
    consumer.consume(message);
    assertThat(notifications.countByEventId(eventId)).isEqualTo(2);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM consumed_events WHERE event_id=?", Long.class, eventId))
        .isEqualTo(1);
  }

  @Autowired dev.network.notification.preferences.JobAlertPreferences preferences;

  @Test
  void alertPreferenceEligibilityAndSemanticDeduplicationAreAtomic() {
    String actor = UUID.randomUUID().toString(),
        recipient = UUID.randomUUID().toString(),
        job = UUID.randomUUID().toString(),
        match = UUID.randomUUID().toString();
    var e =
        new HashMap<String, Object>(
            Map.of(
                "eventId",
                UUID.randomUUID().toString(),
                "eventType",
                "hiring.job.alert",
                "schemaVersion",
                1,
                "occurredAt",
                "2026-09-27T00:00:00Z",
                "aggregateId",
                job,
                "aggregateVersion",
                1,
                "producer",
                "hiring-service",
                "correlationId",
                job,
                "causationId",
                job,
                "payload",
                Map.of("actorId", actor, "recipientId", recipient, "matchId", match)));
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(Boolean.class)))
        .thenThrow(new IllegalStateException("Dependency unavailable"));
    assertThatThrownBy(() -> consumer.consume(json.writeValueAsString(e)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM consumed_events WHERE event_id=?",
                Long.class,
                e.get("eventId")))
        .isZero();
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(Boolean.class)))
        .thenReturn(true);
    consumer.consume(json.writeValueAsString(e));
    consumer.consume(json.writeValueAsString(e));
    e.put("eventId", UUID.randomUUID().toString());
    consumer.consume(json.writeValueAsString(e));
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_id=? AND resource_id=?",
                Long.class,
                recipient,
                job))
        .isEqualTo(1);
    var principal =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
            .header("alg", "test")
            .subject(recipient)
            .build();
    assertThat(
            inbox.list(principal, 0, 100).stream()
                .filter(n -> n.resourceId().equals(job))
                .findFirst()
                .orElseThrow()
                .message())
        .isEqualTo("A new job matches your saved search.");
    preferences.set(recipient, false);
    e.put("aggregateId", UUID.randomUUID().toString());
    e.put("eventId", UUID.randomUUID().toString());
    e.put(
        "payload",
        Map.of(
            "actorId", actor, "recipientId", recipient, "matchId", UUID.randomUUID().toString()));
    consumer.consume(json.writeValueAsString(e));
    assertThat(notifications.countByEventId((String) e.get("eventId"))).isZero();
  }

  @Autowired dev.network.web.stream.DurableStream stream;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @Test
  void notificationReplayMuteFailureAndConcurrentReadAreAtomic() throws Exception {
    String actor = UUID.randomUUID().toString(),
        owner = UUID.randomUUID().toString(),
        id = UUID.randomUUID().toString();
    String before = stream.boundary(owner);
    var event = new HashMap<String, Object>();
    event.put("eventId", id);
    event.put("eventType", "message.sent");
    event.put("schemaVersion", 1);
    event.put("occurredAt", java.time.Instant.now().toString());
    event.put("aggregateId", UUID.randomUUID().toString());
    event.put("aggregateVersion", 1);
    event.put("producer", "messaging-service");
    event.put("correlationId", id);
    event.put("causationId", id);
    event.put("payload", Map.of("actorId", actor, "recipientId", owner));
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.endsWith("/notification-eligibility"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(
                    dev.network.notification.events.NotificationConsumer.MessageEligibility.class)))
        .thenThrow(new IllegalStateException("Authority unavailable"));
    assertThatThrownBy(() -> consumer.consume(json.writeValueAsString(event)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM consumed_events WHERE event_id=?", Long.class, id))
        .isZero();
    assertThat(stream.replay(owner, before, 50)).isEmpty();
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.endsWith("/notification-eligibility"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(
                    dev.network.notification.events.NotificationConsumer.MessageEligibility.class)))
        .thenReturn(
            new dev.network.notification.events.NotificationConsumer.MessageEligibility(false));
    consumer.consume(json.writeValueAsString(event));
    assertThat(notifications.countByEventId(id)).isZero();
    assertThat(stream.replay(owner, before, 50)).isEmpty();
    event.put("eventId", UUID.randomUUID().toString());
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.endsWith("/notification-eligibility"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(
                    dev.network.notification.events.NotificationConsumer.MessageEligibility.class)))
        .thenReturn(
            new dev.network.notification.events.NotificationConsumer.MessageEligibility(true));
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              consumer.consume(json.writeValueAsString(event));
              status.setRollbackOnly();
            });
    assertThat(stream.replay(owner, before, 50)).isEmpty();
    assertThat(notifications.countByEventId((String) event.get("eventId"))).isZero();
    consumer.consume(json.writeValueAsString(event));
    consumer.consume(json.writeValueAsString(event));
    var created = stream.replay(owner, before, 50);
    assertThat(created).hasSize(1);
    assertThat(created.getFirst().eventType()).isEqualTo("notification.created");
    var principal =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture")
            .header("alg", "fixture")
            .subject(owner)
            .build();
    assertThat(notifications.countByRecipientIdAndReadAtIsNull(owner)).isEqualTo(1);
    try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var first = pool.submit(() -> inbox.read(principal, created.getFirst().resourceId()));
      var second = pool.submit(() -> inbox.read(principal, created.getFirst().resourceId()));
      assertThat(first.get().readAt()).isEqualTo(second.get().readAt());
    }
    assertThat(notifications.countByRecipientIdAndReadAtIsNull(owner)).isZero();
    assertThat(stream.replay(owner, created.getFirst().cursor(), 50))
        .extracting(dev.network.web.stream.DurableStream.Event::eventType)
        .containsExactly("notification.read");
  }
}
