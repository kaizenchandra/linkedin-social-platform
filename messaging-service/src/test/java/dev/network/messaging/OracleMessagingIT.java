package dev.network.messaging;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = "network.outbox.enabled=false")
class OracleMessagingIT {
  static OracleContainer oracle;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    if (System.getenv("TEST_DB_URL") == null) {
      oracle = new OracleContainer("gvenzl/oracle-free:23.9-slim-faststart");
      oracle.start();
      r.add("spring.datasource.url", oracle::getJdbcUrl);
      r.add("spring.datasource.username", oracle::getUsername);
      r.add("spring.datasource.password", oracle::getPassword);
    } else {
      r.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
      r.add("spring.datasource.username", () -> System.getenv("TEST_MESSAGING_DB_USER"));
      r.add("spring.datasource.password", () -> System.getenv("TEST_MESSAGING_DB_PASSWORD"));
    }
  }

  @Autowired MessagingService messages;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  dev.network.web.ServiceHttp http;

  @org.junit.jupiter.api.BeforeEach
  void policy() {
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(MessagingService.Policy[].class)))
        .thenAnswer(
            i -> {
              Map<String, Object> body = i.getArgument(1);
              String id = ((List<String>) body.get("memberIds")).getFirst();
              return new MessagingService.Policy[] {new MessagingService.Policy(id, true, true)};
            });
  }

  @Test
  void concurrentPairAndSendUniquenessAndMonotonicReads() throws Exception {
    String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    String id;
    try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          pool.submit(
              () -> {
                barrier.await();
                return messages.start(a, b);
              });
      var second =
          pool.submit(
              () -> {
                barrier.await();
                return messages.start(b, a);
              });
      var c = first.get();
      assertThat(second.get().id()).isEqualTo(c.id());
      id = c.id();
      String key = UUID.randomUUID().toString();
      var one = pool.submit(() -> messages.send(a, id, key, "漢".repeat(4000)));
      var two = pool.submit(() -> messages.send(a, id, key, "漢".repeat(4000)));
      assertThat(one.get().id()).isEqualTo(two.get().id());
      assertThatThrownBy(() -> messages.send(a, id, key, "different"))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    var first = messages.history(b, id, null, 1);
    assertThat(first.items()).hasSize(1);
    var second = messages.send(a, id, UUID.randomUUID().toString(), "next");
    assertThat(messages.get(b, id).unreadCount()).isEqualTo(2);
    messages.read(b, id, second.id());
    assertThat(messages.read(b, id, first.items().getFirst().id()).readPosition()).isEqualTo(2);
    assertThat(messages.get(b, id).unreadCount()).isZero();
    assertThat(messages.history(b, id, first.nextCursor(), 20).items())
        .extracting(MessagingService.Message::id)
        .containsExactly(second.id());
    assertThat(messages.list(b, null, 20).items()).hasSize(1);
  }

  @Test
  void participantsOnlyAndDisconnectedHistorySurvives() {
    String a = UUID.randomUUID().toString(),
        b = UUID.randomUUID().toString(),
        c = UUID.randomUUID().toString();
    var pair = messages.start(a, b);
    var message = messages.send(a, pair.id(), UUID.randomUUID().toString(), "private");
    assertThatThrownBy(() -> messages.history(c, pair.id(), null, 20))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(() -> messages.read(b, pair.id(), UUID.randomUUID().toString()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(MessagingService.Policy[].class)))
        .thenReturn(new MessagingService.Policy[] {new MessagingService.Policy(b, false, false)});
    assertThatThrownBy(() -> messages.send(a, pair.id(), UUID.randomUUID().toString(), "new"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(messages.history(b, pair.id(), null, 20).items()).hasSize(1);
    assertThat(messages.send(a, pair.id(), message.clientMessageId(), "private").id())
        .isEqualTo(message.id());
  }

  @Autowired dev.network.web.stream.DurableStream stream;
  @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @Autowired org.springframework.jdbc.core.JdbcTemplate database;

  @Test
  void replayRollsBackWithBusinessTransactionAndRequiresExplicitTransaction() {
    String actor = UUID.randomUUID().toString(), resource = UUID.randomUUID().toString();
    String before = stream.boundary(actor);
    double persisted = metrics.counter("network.stream.events.persisted").count();
    var event =
        new dev.network.web.stream.DurableStream.Update(actor, "message.created", resource, 1);
    assertThatThrownBy(() -> stream.append(List.of(event)))
        .isInstanceOf(IllegalStateException.class);
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    tx.executeWithoutResult(
        status -> {
          stream.append(List.of(event));
          status.setRollbackOnly();
        });
    assertThat(stream.boundary(actor)).isEqualTo(before);
    assertThat(metrics.counter("network.stream.events.persisted").count()).isEqualTo(persisted);
    assertThat(stream.replay(actor, before, 50)).isEmpty();
    tx.executeWithoutResult(status -> stream.append(List.of(event)));
    var replay = stream.replay(actor, before, 50);
    assertThat(replay).hasSize(1);
    assertThat(stream.position(actor, replay.getFirst().cursor())).isEqualTo(1);
  }

  @Test
  void ownerLockPreventsLateCommitBehindAcknowledgedCursor() throws Exception {
    String actor = UUID.randomUUID().toString(), resource = UUID.randomUUID().toString();
    String before = stream.boundary(actor);
    var allocated = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var secondStarted = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          pool.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .executeWithoutResult(
                          s -> {
                            stream.append(
                                List.of(
                                    new dev.network.web.stream.DurableStream.Update(
                                        actor, "message.created", resource, 1)));
                            allocated.countDown();
                            try {
                              if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                                throw new AssertionError("release timeout");
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new RuntimeException(e);
                            }
                          }));
      assertThat(allocated.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      var second =
          pool.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .executeWithoutResult(
                          s -> {
                            secondStarted.countDown();
                            stream.append(
                                List.of(
                                    new dev.network.web.stream.DurableStream.Update(
                                        actor, "message.created", resource, 2)));
                          }));
      assertThat(secondStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      try {
        assertThat(
                database.queryForObject(
                    "SELECT COUNT(*) FROM stream_events WHERE owner_id=?", Long.class, actor))
            .isZero();
        assertThatThrownBy(() -> second.get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
            .isInstanceOf(java.util.concurrent.TimeoutException.class);
      } finally {
        release.countDown();
      }
      first.get(5, java.util.concurrent.TimeUnit.SECONDS);
      second.get(5, java.util.concurrent.TimeUnit.SECONDS);
      var replay = stream.replay(actor, before, 50);
      assertThat(replay)
          .extracting(dev.network.web.stream.DurableStream.Event::resourceVersion)
          .containsExactly(1L, 2L);
      assertThat(stream.replay(actor, replay.getFirst().cursor(), 50))
          .containsExactly(replay.getLast());
    } finally {
      release.countDown();
    }
  }

  @Test
  void replayCursorsAreOwnerScopedBoundedAndResetAfterRetention() {
    String actor = UUID.randomUUID().toString(), other = UUID.randomUUID().toString();
    String before = stream.boundary(actor);
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    tx.executeWithoutResult(
        s ->
            stream.append(
                List.of(
                    new dev.network.web.stream.DurableStream.Update(
                        actor, "message.created", UUID.randomUUID().toString(), 1))));
    assertThatThrownBy(() -> stream.replay(other, before, 50))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> stream.replay(actor, "invalid", 50))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> stream.replay(actor, before, 51))
        .isInstanceOf(IllegalArgumentException.class);
    String future =
        Base64.getUrlEncoder()
            .encodeToString(
                ("1|messaging|" + actor + "|999999")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThatThrownBy(() -> stream.replay(actor, future, 50))
        .isInstanceOfSatisfying(
            org.springframework.web.server.ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    String current = stream.boundary(actor);
    assertThat(stream.pruneOwner(actor, java.time.Instant.now().plusSeconds(1))).isEqualTo(1);
    assertThatThrownBy(() -> stream.replay(actor, before, 50))
        .isInstanceOfSatisfying(
            org.springframework.web.server.ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(410));
    assertThat(stream.replay(actor, current, 50)).isEmpty();
    tx.executeWithoutResult(
        s ->
            stream.append(
                List.of(
                    new dev.network.web.stream.DurableStream.Update(
                        actor, "message.created", UUID.randomUUID().toString(), 2))));
    assertThat(stream.position(actor, stream.replay(actor, current, 50).getFirst().cursor()))
        .isEqualTo(2);
  }

  @Test
  void businessRollbackAndReadEventsShareTransactions() {
    String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
    String id = messages.start(a, b).id();
    String before = stream.boundary(b);
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    tx.executeWithoutResult(
        status -> {
          messages.send(a, id, UUID.randomUUID().toString(), "Must roll back");
          status.setRollbackOnly();
        });
    assertThat(messages.history(b, id, null, 100).items()).isEmpty();
    assertThat(stream.replay(b, before, 50)).isEmpty();
    var first = messages.send(a, id, UUID.randomUUID().toString(), "Private fixture");
    assertThat(stream.replay(b, before, 50))
        .extracting(dev.network.web.stream.DurableStream.Event::eventType)
        .containsExactly("message.created");
    String ownBefore = stream.boundary(b), otherBefore = stream.boundary(a);
    var read = messages.read(b, id, first.id());
    assertThat(read.readVersion()).isEqualTo(1);
    assertThat(messages.read(b, id, first.id()).readVersion()).isEqualTo(1);
    assertThat(stream.replay(b, ownBefore, 50))
        .extracting(dev.network.web.stream.DurableStream.Event::eventType)
        .containsExactly("conversation.read");
    assertThat(stream.replay(a, otherBefore, 50)).isEmpty();
    assertThat(messages.unread(b)).isZero();
    assertThat(messages.unread(a)).isZero();
  }

  @Test
  void privatePreferencesAndMessageUnarchiveAreTransactionalAndRetriesHaveNoNewEffects() {
    String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
    String id = messages.start(a, b).id();
    var muted = messages.preferences(a, id, true, true);
    assertThat(muted.muted()).isTrue();
    assertThat(muted.archived()).isTrue();
    assertThat(muted.readPosition()).isZero();
    assertThat(messages.preferences(a, id, true, true).preferenceVersion())
        .isEqualTo(muted.preferenceVersion());
    assertThat(messages.list(a, null, 100).items()).isEmpty();
    assertThat(messages.list(a, null, 100, true, false).items()).hasSize(1);
    assertThat(messages.get(b, id).muted()).isFalse();
    assertThat(messages.get(b, id).archived()).isFalse();
    assertThat(messages.notificationAllowed(a, id)).isFalse();
    assertThat(messages.notificationAllowed(b, id)).isTrue();
    assertThat(messages.notificationAllowed(UUID.randomUUID().toString(), id)).isFalse();
    String before = stream.boundary(a);
    var sent = messages.send(b, id, UUID.randomUUID().toString(), "Incoming");
    var state = messages.get(a, id);
    assertThat(state.archived()).isFalse();
    assertThat(state.muted()).isTrue();
    assertThat(state.unreadCount()).isEqualTo(1);
    assertThat(state.readPosition()).isZero();
    assertThat(stream.replay(a, before, 50))
        .extracting(dev.network.web.stream.DurableStream.Event::eventType)
        .containsExactly("conversation.preferences", "message.created");
    messages.preferences(b, id, null, true);
    messages.send(b, id, sent.clientMessageId(), sent.body());
    assertThat(messages.get(b, id).archived()).isTrue();
    messages.send(b, id, UUID.randomUUID().toString(), "New outgoing");
    assertThat(messages.get(b, id).archived()).isFalse();
  }

  @Test
  void concurrentDevicesCannotRegressReadPositionOrNotifyOtherParticipant() throws Exception {
    String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
    String id = messages.start(a, b).id();
    var one = messages.send(b, id, UUID.randomUUID().toString(), "First");
    var two = messages.send(b, id, UUID.randomUUID().toString(), "Second");
    String start = stream.boundary(a), other = stream.boundary(b);
    try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var first = pool.submit(() -> messages.read(a, id, two.id()));
      var second = pool.submit(() -> messages.read(a, id, one.id()));
      first.get();
      second.get();
    }
    assertThat(messages.get(a, id).readPosition()).isEqualTo(two.sequence());
    assertThat(messages.get(a, id).unreadCount()).isZero();
    assertThat(stream.replay(a, start, 50))
        .extracting(dev.network.web.stream.DurableStream.Event::resourceVersion)
        .isSorted();
    assertThat(stream.replay(b, other, 50)).isEmpty();
    long version = messages.get(a, id).readVersion();
    messages.read(a, id, one.id());
    assertThat(messages.get(a, id).readVersion()).isEqualTo(version);
  }
}
