package dev.network.content.post;

import dev.network.content.events.EventWriter;
import dev.network.content.feed.Cursor;
import dev.network.web.Pages;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContentService {
  private final PostRepository posts;
  private final CommentRepository comments;
  private final LikeRepository likes;
  private final EventWriter events;
  private final Clock clock;

  public ContentService(
      PostRepository p, CommentRepository c, LikeRepository l, EventWriter e, Clock clock) {
    posts = p;
    comments = c;
    likes = l;
    events = e;
    this.clock = clock;
  }

  public record PostView(
      String id,
      String authorId,
      String body,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record Detail(PostView post, long likeCount, long commentCount) {}

  public record CommentView(
      String id, String postId, String authorId, String body, Instant createdAt) {}

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
  }

  private Post locked(String id) {
    return posts.lock(id).orElseThrow(this::missing);
  }

  private void owns(String actor, String owner) {
    if (!actor.equals(owner)) throw missing();
  }

  @Transactional
  public PostView create(String actor, String body) {
    var p = new Post();
    p.id = UUID.randomUUID().toString();
    p.authorId = actor;
    p.body = body;
    p.createdAt = now();
    p.updatedAt = p.createdAt;
    return view(posts.saveAndFlush(p));
  }

  @Transactional
  public PostView edit(String actor, String id, String body) {
    var p = locked(id);
    owns(actor, p.authorId);
    p.body = body;
    p.updatedAt = now();
    posts.flush();
    return view(p);
  }

  @Transactional
  public void delete(String actor, String id) {
    var p = locked(id);
    owns(actor, p.authorId);
    posts.delete(p);
  }

  @Transactional(readOnly = true)
  public Detail detail(String id) {
    var p = posts.findById(id).orElseThrow(this::missing);
    return new Detail(view(p), likes.countByPostId(id), comments.countByPostId(id));
  }

  @Transactional
  public void like(String actor, String id) {
    var p = locked(id);
    if (likes.findByPostIdAndMemberId(id, actor).isPresent()) return;
    var l = new PostLike();
    l.id = UUID.randomUUID().toString();
    l.postId = id;
    l.memberId = actor;
    l.createdAt = now();
    likes.saveAndFlush(l);
    events.write("post.liked", p.id, p.version, actor, p.authorId);
  }

  @Transactional
  public void unlike(String actor, String id) {
    locked(id);
    likes.findByPostIdAndMemberId(id, actor).ifPresent(likes::delete);
  }

  @Transactional
  public CommentView comment(String actor, String id, String body) {
    var p = locked(id);
    var c = new Comment();
    c.id = UUID.randomUUID().toString();
    c.postId = id;
    c.authorId = actor;
    c.body = body;
    c.createdAt = now();
    comments.save(c);
    events.write("post.commented", p.id, p.version, actor, p.authorId);
    return view(c);
  }

  @Transactional
  public void deleteComment(String actor, String id) {
    var c = comments.findById(id).orElseThrow(this::missing);
    owns(actor, c.authorId);
    locked(c.postId);
    comments.delete(c);
  }

  @Transactional(readOnly = true)
  public List<CommentView> comments(String id, int page, int size) {
    if (!posts.existsById(id)) throw missing();
    return comments
        .findByPostIdOrderByCreatedAtAscIdAsc(
            id, PageRequest.of(Pages.page(page), Pages.size(size)))
        .stream()
        .map(this::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public Pages.Slice<PostView> timeline(List<String> authors, String cursor, int size) {
    int limit = Pages.size(size);
    var position = Cursor.parse(cursor, clock);
    var rows = posts.feed(authors, position.time(), position.id(), PageRequest.of(0, limit + 1));
    boolean more = rows.size() > limit;
    var items = rows.stream().limit(limit).map(this::view).toList();
    String next = null;
    if (more) {
      var last = items.getLast();
      next = new Cursor(last.createdAt(), last.id()).encode();
    }
    return new Pages.Slice<>(items, next);
  }

  private PostView view(Post p) {
    return new PostView(p.id, p.authorId, p.body, p.createdAt, p.updatedAt, p.version);
  }

  private CommentView view(Comment c) {
    return new CommentView(c.id, c.postId, c.authorId, c.body, c.createdAt);
  }
}
