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
}
