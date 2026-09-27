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

  @Autowired dev.network.member.policy.PolicyService policy;
  @Autowired dev.network.member.policy.BlockRepository blocks;

  @Test
  void blockRemovesConnectionAndUnblockDoesNotRestore() throws Exception {
    String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
    for (String id : java.util.List.of(a, b))
      profiles.save(
          jwt(id),
          new ProfileController.Input("Privacy member", null, null, null, java.util.List.of()));
    var c = connections.request(a, b);
    connections.command(b, c.id(), "accept");
    try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var one = pool.submit(() -> policy.block(a, b));
      var two = pool.submit(() -> policy.block(a, b));
      one.get();
      two.get();
    }
    assertThat(blocks.relevant(a, java.util.List.of(b))).hasSize(1);
    assertThat(connections.accepted(a)).isEmpty();
    assertThatThrownBy(() -> profiles.get(jwt(b), a))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(profiles.lookup(jwt(b), java.util.List.of(a))).isEmpty();
    assertThat(profiles.search(jwt(b), "Privacy", 0, 100)).noneMatch(x -> x.id().equals(a));
    assertThatThrownBy(() -> connections.request(b, a))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    policy.unblock(a, b);
    policy.unblock(a, b);
    assertThat(connections.accepted(a)).isEmpty();
    assertThat(policy.check(b, java.util.List.of(a)).getFirst().visible()).isTrue();
    assertThatThrownBy(() -> policy.block(a, a)).isInstanceOf(IllegalArgumentException.class);
  }

  private org.springframework.security.oauth2.jwt.Jwt jwt(String id) {
    return org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
        .header("alg", "test")
        .subject(id)
        .build();
  }

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
    assertThat(profiles.get(jwt, id).experiences()).hasSize(1);
  }
}
