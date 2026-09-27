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
    @Autowired
    ProfileController profiles;
    @Autowired
    dev.network.member.connection.ConnectionService connections;
    @Autowired
    dev.network.member.connection.ConnectionRepository relationships;
    @Autowired
    dev.network.member.policy.PolicyService policy;
    @Autowired
    dev.network.member.policy.BlockRepository blocks;
    @Autowired
    dev.network.member.follow.FollowService follows;
    @Autowired
    dev.network.member.discovery.MemberDiscovery discovery;
    @Autowired
    HiringProfileController snapshots;

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

    @Test
    void followsAreIndependentIdempotentAndRemovedByBlocking() throws Exception {
        String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
        for (String id : java.util.List.of(a, b))
            profiles.save(
                    jwt(id), new ProfileController.Input("Follower", null, null, null, java.util.List.of()));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var one =
                    pool.submit(
                            () -> {
                                barrier.await();
                                follows.follow(a, b);
                                return true;
                            });
            var two =
                    pool.submit(
                            () -> {
                                barrier.await();
                                follows.follow(a, b);
                                return true;
                            });
            assertThat(one.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(two.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        assertThat(follows.ids(a)).containsExactly(b);
        assertThat(connections.accepted(a)).isEmpty();
        follows.follow(b, a);
        var c = connections.request(a, b);
        connections.command(b, c.id(), "accept");
        connections.command(a, c.id(), "remove");
        assertThat(follows.status(a, b)).isTrue();
        policy.block(a, b);
        assertThat(follows.ids(a)).isEmpty();
        assertThat(follows.ids(b)).isEmpty();
        assertThatThrownBy(() -> follows.follow(b, a))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        policy.unblock(a, b);
        assertThat(follows.ids(a)).isEmpty();
        follows.unfollow(a, b);
        follows.unfollow(a, b);
        assertThatThrownBy(() -> follows.follow(a, a)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void followRacingWithBlockNeverSurvivesBlock() throws Exception {
        String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
        for (String id : java.util.List.of(a, b))
            profiles.save(
                    jwt(id), new ProfileController.Input("Race", null, null, null, java.util.List.of()));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var follow =
                    pool.submit(
                            () -> {
                                barrier.await();
                                try {
                                    follows.follow(a, b);
                                } catch (org.springframework.web.server.ResponseStatusException e) {
                                    assertThat(e.getStatusCode().value()).isEqualTo(404);
                                }
                                return true;
                            });
            var block =
                    pool.submit(
                            () -> {
                                barrier.await();
                                policy.block(b, a);
                                return true;
                            });
            follow.get(20, java.util.concurrent.TimeUnit.SECONDS);
            block.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(follows.status(a, b)).isFalse();
    }

    @Test
    void mutualSuggestionsAreStableAndRespectEveryExclusion() {
        var ids =
                java.util.stream.IntStream.range(0, 7).mapToObj(i -> UUID.randomUUID().toString()).toList();
        for (String id : ids)
            profiles.save(
                    jwt(id), new ProfileController.Input("Discovery", null, null, null, java.util.List.of()));
        String a = ids.get(0),
                f = ids.get(1),
                g = ids.get(2),
                candidate = ids.get(3),
                followed = ids.get(4),
                pending = ids.get(5),
                blocked = ids.get(6);
        for (String[] pair :
                java.util.List.of(
                        new String[]{a, f},
                        new String[]{a, g},
                        new String[]{f, candidate},
                        new String[]{g, candidate},
                        new String[]{f, followed},
                        new String[]{f, pending},
                        new String[]{f, blocked})) {
            var c = connections.request(pair[0], pair[1]);
            connections.command(pair[1], c.id(), "accept");
        }
        follows.follow(a, followed);
        connections.request(a, pending);
        policy.block(blocked, a);
        var result = discovery.suggest(a, 20);
        assertThat(result)
                .extracting(dev.network.member.discovery.MemberDiscovery.Suggestion::memberId)
                .containsExactly(candidate);
        assertThat(result.getFirst().reason()).isEqualTo("Connections in common");
        assertThat(discovery.suggest(a, 20)).isEqualTo(result);
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

    @Test
    void experienceOnlyUpdatesAdvanceCoherentSnapshotVersion() throws Exception {
        String id = UUID.randomUUID().toString();
        var first =
                profiles.save(
                        jwt(id),
                        new ProfileController.Input(
                                "Snapshot member",
                                "Engineer",
                                "Summary",
                                "Local",
                                java.util.List.of(
                                        new ProfileController.ExperienceInput("Old", "Engineer", "2020-01", null))));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var write =
                    pool.submit(
                            () -> {
                                barrier.await();
                                return profiles.save(
                                        jwt(id),
                                        new ProfileController.Input(
                                                "Snapshot member",
                                                "Engineer",
                                                "Summary",
                                                "Local",
                                                java.util.List.of(
                                                        new ProfileController.ExperienceInput(
                                                                "New", "Engineer", "2020-01", null))));
                            });
            var read =
                    pool.submit(
                            () -> {
                                barrier.await();
                                return snapshots.snapshot(new HiringProfileController.Request(id));
                            });
            var updated = write.get();
            var concurrent = read.get();
            assertThat(updated.version()).isGreaterThan(first.version());
            assertThat(concurrent.version())
                    .isEqualTo(
                            concurrent.experiences().getFirst().company().equals("Old")
                                    ? first.version()
                                    : updated.version());
            var current = snapshots.snapshot(new HiringProfileController.Request(id));
            assertThat(current.version()).isEqualTo(updated.version());
            assertThat(current.experiences().getFirst().company()).isEqualTo("New");
        }
    }
}
