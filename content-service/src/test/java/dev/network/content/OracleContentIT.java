package dev.network.content;

import static org.assertj.core.api.Assertions.*;

import dev.network.content.post.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(
    properties = {"CONTENT_CLIENT_SECRET=test-only-unused", "network.outbox.enabled=false"})
class OracleContentIT {
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
      r.add("spring.datasource.username", () -> System.getenv("TEST_CONTENT_DB_USER"));
      r.add(
          "spring.datasource.password",
          () ->
              System.getenv()
                  .getOrDefault("TEST_CONTENT_DB_PASSWORD", System.getenv("TEST_DB_PASSWORD")));
    }
  }

  @Autowired ContentService content;

  @Test
  void concurrentLikesOwnershipAndCascadeDeletion() throws Exception {
    String author = UUID.randomUUID().toString(), reader = UUID.randomUUID().toString();
    var post = content.create(author, "漢".repeat(3000));
    try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var a = pool.submit(() -> content.like(reader, post.id()));
      var b = pool.submit(() -> content.like(reader, post.id()));
      a.get();
      b.get();
    }
    assertThat(content.detail(post.id()).likeCount()).isEqualTo(1);
    assertThatThrownBy(() -> content.edit(reader, post.id(), "forged"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    var c = content.comment(reader, post.id(), "Comment");
    assertThatThrownBy(() -> content.deleteComment(author, c.id()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    content.delete(author, post.id());
    assertThatThrownBy(() -> content.comment(reader, post.id(), "late"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void cursorPagesDoNotRepeatOrSkip() {
    String author = UUID.randomUUID().toString();
    var ids = new HashSet<String>();
    for (int i = 0; i < 7; i++) ids.add(content.create(author, "post " + i).id());
    var seen = new HashSet<String>();
    String cursor = null;
    do {
      var page = content.timeline(List.of(author), cursor, 2);
      for (var p : page.items()) assertThat(seen.add(p.id())).isTrue();
      cursor = page.nextCursor();
    } while (cursor != null);
    assertThat(seen).isEqualTo(ids);
  }
}
