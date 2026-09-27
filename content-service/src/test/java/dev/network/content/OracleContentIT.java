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

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  dev.network.content.feed.MemberClient members;

  @org.junit.jupiter.api.BeforeEach
  void policy() {
    org.mockito.Mockito.when(
            members.policy(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
        .thenAnswer(
            inv -> {
              List<String> ids = inv.getArgument(1);
              var map = new HashMap<String, dev.network.content.feed.MemberClient.Decision>();
              ids.forEach(
                  id ->
                      map.put(
                          id, new dev.network.content.feed.MemberClient.Decision(id, true, true)));
              return map;
            });
  }

  @Autowired dev.network.content.moderation.ModerationService moderation;

  @Test
  void blockedCommentersAndLikersDoNotLeakThroughBodiesOrCounts() {
    String author = UUID.randomUUID().toString(), peer = UUID.randomUUID().toString();
    var p = content.create(author, "Public");
    content.comment(peer, p.id(), "Blocked body");
    content.like(peer, p.id());
    org.mockito.Mockito.when(members.policy(author, List.of(peer)))
        .thenReturn(
            Map.of(peer, new dev.network.content.feed.MemberClient.Decision(peer, false, false)));
    assertThat(content.comments(author, p.id(), 0, 20)).isEmpty();
    assertThat(content.detail(author, p.id()).commentCount()).isZero();
    assertThat(content.detail(author, p.id()).likeCount()).isZero();
  }

  @Test
  @org.springframework.security.test.context.support.WithMockUser(roles = "moderator")
  void moderationIsAuditedAndNeverResurrectsAuthorDeletedContent() {
    String author = UUID.randomUUID().toString(),
        reporter = UUID.randomUUID().toString(),
        moderator = UUID.randomUUID().toString();
    var p = content.create(author, "Reported private text", Post.Visibility.CONNECTIONS);
    var r =
        moderation.submit(
            reporter,
            dev.network.content.moderation.ModerationService.TargetType.POST,
            p.id(),
            dev.network.content.moderation.ModerationService.Reason.SPAM,
            null);
    assertThat(moderation.submit(reporter, r.targetType(), p.id(), r.reason(), null).id())
        .isEqualTo(r.id());
    assertThat(moderation.own(author, 0, 20)).isEmpty();
    assertThat(moderation.inspect(moderator, r.id(), "Investigate report").body())
        .isEqualTo("Reported private text");
    moderation.act(
        moderator,
        r.id(),
        dev.network.content.moderation.ModerationService.Action.HIDE,
        "Reported spam");
    assertThatThrownBy(() -> content.detail(author, p.id()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(content.timeline(author, List.of(author), null, 20).items()).isEmpty();
    assertThatThrownBy(() -> content.like(reporter, p.id()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    moderation.act(
        moderator,
        r.id(),
        dev.network.content.moderation.ModerationService.Action.RESTORE,
        "Reviewed context");
    assertThat(content.detail(author, p.id()).post().visibility())
        .isEqualTo(Post.Visibility.CONNECTIONS);
    assertThat(moderation.history(r.id(), 0, 20)).hasSize(3);
    var comment = content.comment(reporter, p.id(), "Reported comment");
    var cr =
        moderation.submit(
            author,
            dev.network.content.moderation.ModerationService.TargetType.COMMENT,
            comment.id(),
            dev.network.content.moderation.ModerationService.Reason.HARASSMENT,
            null);
    moderation.act(
        moderator,
        cr.id(),
        dev.network.content.moderation.ModerationService.Action.HIDE,
        "Harassment");
    assertThat(content.comments(author, p.id(), 0, 20)).isEmpty();
    assertThat(content.detail(author, p.id()).commentCount()).isZero();
    content.delete(author, p.id());
    assertThatThrownBy(
            () ->
                moderation.act(
                    moderator,
                    r.id(),
                    dev.network.content.moderation.ModerationService.Action.RESTORE,
                    "Cannot resurrect"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                moderation.act(
                    moderator,
                    cr.id(),
                    dev.network.content.moderation.ModerationService.Action.RESTORE,
                    "Deleted parent"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(moderation.inspect(moderator, r.id(), "Verify tombstone").body()).isNull();
  }

  @Test
  @org.springframework.security.test.context.support.WithMockUser
  void ordinaryUsersCannotInvokeModeration() {
    assertThatThrownBy(() -> moderation.queue(null, 20))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void visibilityAndBlockingApplyToAllContentPaths() {
    String author = UUID.randomUUID().toString(), reader = UUID.randomUUID().toString();
    var post = content.create(author, "Private", Post.Visibility.CONNECTIONS);
    var comment = content.comment(author, post.id(), "Private comment");
    org.mockito.Mockito.when(members.policy(reader, List.of(author)))
        .thenReturn(
            Map.of(
                author, new dev.network.content.feed.MemberClient.Decision(author, true, false)));
    assertThatThrownBy(() -> content.detail(reader, post.id()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(content.timeline(reader, List.of(author), null, 20).items()).isEmpty();
    assertThatThrownBy(() -> content.comments(reader, post.id(), 0, 20))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(() -> content.like(reader, post.id()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(() -> content.comment(reader, post.id(), "No"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    content.edit(author, post.id(), "Public", Post.Visibility.MEMBERS);
    assertThat(content.detail(reader, post.id()).post().visibility())
        .isEqualTo(Post.Visibility.MEMBERS);
    org.mockito.Mockito.when(members.policy(reader, List.of(author)))
        .thenReturn(
            Map.of(
                author, new dev.network.content.feed.MemberClient.Decision(author, false, false)));
    assertThatThrownBy(() -> content.detail(reader, post.id()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(content.timeline(reader, List.of(author), null, 20).items()).isEmpty();
  }

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
    assertThat(content.detail(author, post.id()).likeCount()).isEqualTo(1);
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
      var page = content.timeline(author, List.of(author), cursor, 2);
      for (var p : page.items()) assertThat(seen.add(p.id())).isTrue();
      cursor = page.nextCursor();
    } while (cursor != null);
    assertThat(seen).isEqualTo(ids);
  }
}
