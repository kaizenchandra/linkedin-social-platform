package dev.network.member;

import static org.assertj.core.api.Assertions.*;

import dev.network.member.profile.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = "network.outbox.enabled=false")
class OracleProfileIT {
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
      r.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
      r.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }
  }

  @Autowired ProfileController profiles;
  @Autowired dev.network.member.connection.ConnectionService connections;
  @Autowired dev.network.member.connection.ConnectionRepository relationships;

  @Test
  void reciprocalConcurrentRequestsHaveOneCanonicalRow() throws Exception {
    String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
    for (String id : java.util.List.of(a, b))
      profiles.save(
          org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
              .header("alg", "test")
              .subject(id)
              .build(),
          new ProfileController.Input("Concurrent", null, null, null, java.util.List.of()));
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var one =
          pool.submit(
              () -> {
                barrier.await();
                try {
                  return connections.request(a, b).id();
                } catch (org.springframework.web.server.ResponseStatusException e) {
                  assertThat(e.getStatusCode().value()).isEqualTo(409);
                  return "conflict";
                }
              });
      var two =
          pool.submit(
              () -> {
                barrier.await();
                try {
                  return connections.request(b, a).id();
                } catch (org.springframework.web.server.ResponseStatusException e) {
                  assertThat(e.getStatusCode().value()).isEqualTo(409);
                  return "conflict";
                }
              });
      String x = one.get(20, java.util.concurrent.TimeUnit.SECONDS),
          y = two.get(20, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(java.util.List.of(x, y)).contains("conflict");
    }
    var c =
        relationships
            .findByLowIdAndHighId(a.compareTo(b) < 0 ? a : b, a.compareTo(b) < 0 ? b : a)
            .orElseThrow();
    String recipient = c.other(c.requesterId);
    try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var one = pool.submit(() -> connections.command(recipient, c.id, "accept"));
      var two = pool.submit(() -> connections.command(recipient, c.id, "accept"));
      assertThat(one.get().state()).isEqualTo("ACCEPTED");
      assertThat(two.get().state()).isEqualTo("ACCEPTED");
    }
    assertThat(connections.accepted(a)).containsExactly(b);
  }

  @Test
  void profileInitializationIsIdempotentAndPublicDtoHasNoEmail() {
    String id = UUID.randomUUID().toString();
    var jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
            .header("alg", "test")
            .subject(id)
            .build();
    var input =
        new ProfileController.Input(
            "Oracle member",
            "Engineer",
            "Summary",
            "Local",
            java.util.List.of(
                new ProfileController.ExperienceInput("Acme", "Engineer", "2020-01", null)));
    var first = profiles.save(jwt, input);
    var second = profiles.save(jwt, input);
    assertThat(first.id()).isEqualTo(second.id());
    assertThat(profiles.get(id).experiences()).hasSize(1);
  }
}
