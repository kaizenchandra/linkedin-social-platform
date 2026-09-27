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
  private final dev.network.content.feed.MemberClient members;
  private final PostRepository posts;
  private final CommentRepository comments;
  private final LikeRepository likes;
  private final EventWriter events;
  private final Clock clock;
  private final org.springframework.jdbc.core.JdbcTemplate db;

  public ContentService(
      PostRepository p,
      CommentRepository c,
      LikeRepository l,
      EventWriter e,
      Clock clock,
      dev.network.content.feed.MemberClient members,
      org.springframework.jdbc.core.JdbcTemplate db) {
    this.db = db;
    this.members = members;
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
      long version,
      Post.Visibility visibility,
      List<String> mediaIds) {}

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
    var p = posts.lock(id).orElseThrow(this::missing);
    if (p.deletedAt != null) throw missing();
    return p;
  }

  private void owns(String actor, String owner) {
    if (!actor.equals(owner)) throw missing();
  }

  @Transactional
  public PostView create(String actor, String body) {
    return create(actor, body, Post.Visibility.MEMBERS);
  }

  @Transactional
  public PostView create(String actor, String body, Post.Visibility visibility) {
    var p = new Post();
    p.id = UUID.randomUUID().toString();
    p.authorId = actor;
    p.body = body;
    p.visibility = visibility == null ? Post.Visibility.MEMBERS : visibility;
    p.createdAt = now();
    p.updatedAt = p.createdAt;
    return view(posts.saveAndFlush(p));
  }

  @Transactional
  public PostView edit(String actor, String id, String body) {
    return edit(actor, id, body, null);
  }

  @Transactional
  public PostView edit(String actor, String id, String body, Post.Visibility visibility) {
    var p = locked(id);
    owns(actor, p.authorId);
    if (visibility != null) p.visibility = visibility;
    p.body = body;
    p.updatedAt = now();
    posts.flush();
    return view(p);
  }

  @Transactional
  public void delete(String actor, String id) {
    var p = locked(id);
    owns(actor, p.authorId);
    p.deletedAt = now();
    db.update(
        "UPDATE comments SET deleted_at=? WHERE post_id=?", java.sql.Timestamp.from(now()), id);
    db.update("DELETE FROM post_likes WHERE post_id=?", id);
    db.update("DELETE FROM media_references WHERE resource_id=?", id);
  }

  @Transactional(readOnly = true)
  public Detail detail(String actor, String id) {
    var p = posts.findById(id).orElseThrow(this::missing);
    requireView(actor, p);
    return new Detail(
        view(p),
        visibleCount(actor, id, "post_likes", "member_id"),
        visibleCount(actor, id, "comments", "author_id"));
  }

  @Transactional
  public void like(String actor, String id) {
    var p = locked(id);
    requireView(actor, p);
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
    requireView(actor, locked(id));
    likes.findByPostIdAndMemberId(id, actor).ifPresent(likes::delete);
  }

  @Transactional
  public CommentView comment(String actor, String id, String body) {
    var p = locked(id);
    requireView(actor, p);
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
    requireView(actor, locked(c.postId));
    c.deletedAt = now();
  }

  @Transactional(readOnly = true)
  public List<CommentView> comments(String actor, String id, int page, int size) {
    requireView(actor, posts.findById(id).orElseThrow(this::missing));
    var authors = visibleActors(actor, id, "comments", "author_id");
    if (authors.isEmpty()) return List.of();
    return comments
        .visible(id, authors, PageRequest.of(Pages.page(page), Pages.size(size)))
        .stream()
        .map(this::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public Pages.Slice<PostView> timeline(
      String actor, List<String> authors, String cursor, int size) {
    int limit = Pages.size(size);
    var position = Cursor.parse(cursor, clock);
    var decisions =
        members.policy(actor, authors.stream().filter(id -> !id.equals(actor)).toList());
    var visible =
        authors.stream().filter(id -> id.equals(actor) || decisions.get(id).visible()).toList();
    if (visible.isEmpty()) return new Pages.Slice<>(List.of(), null);
    var connected = new ArrayList<String>();
    connected.add(actor);
    decisions.values().stream()
        .filter(d -> d.visible() && d.connected())
        .forEach(d -> connected.add(d.memberId()));
    var rows =
        posts.feed(
            visible, connected, position.time(), position.id(), PageRequest.of(0, limit + 1));
    boolean more = rows.size() > limit;
    var refs = mediaReferences(rows.stream().limit(limit).map(p -> p.id).toList());
    var items =
        rows.stream().limit(limit).map(p -> view(p, refs.getOrDefault(p.id, List.of()))).toList();
    String next = null;
    if (more) {
      var last = items.getLast();
      next = new Cursor(last.createdAt(), last.id()).encode();
    }
    return new Pages.Slice<>(items, next);
  }

  private List<String> visibleActors(String actor, String postId, String table, String column) {
    String flags = table.equals("comments") ? " AND hidden=0 AND deleted_at IS NULL" : "";
    var ids =
        db.queryForList(
            "SELECT DISTINCT "
                + column
                + " FROM "
                + table
                + " WHERE post_id=?"
                + flags
                + " FETCH FIRST 1001 ROWS ONLY",
            String.class,
            postId);
    if (ids.size() > 1000)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Privacy evaluation exceeds bounded work; resource temporarily unavailable");
    var allowed = new ArrayList<String>();
    for (int start = 0; start < ids.size(); start += 501) {
      var batch = ids.subList(start, Math.min(ids.size(), start + 501));
      var others = batch.stream().filter(id -> !id.equals(actor)).toList();
      if (!others.isEmpty())
        members.policy(actor, others).values().stream()
            .filter(dev.network.content.feed.MemberClient.Decision::visible)
            .forEach(d -> allowed.add(d.memberId()));
      if (batch.contains(actor)) allowed.add(actor);
    }
    return allowed;
  }

  private long visibleCount(String actor, String postId, String table, String column) {
    var ids = visibleActors(actor, postId, table, column);
    if (ids.isEmpty()) return 0;
    var args = new ArrayList<Object>();
    args.add(postId);
    args.addAll(ids);
    String flags = table.equals("comments") ? " AND hidden=0 AND deleted_at IS NULL" : "";
    return db.queryForObject(
        "SELECT COUNT(*) FROM "
            + table
            + " WHERE post_id=?"
            + flags
            + " AND "
            + column
            + " IN ("
            + String.join(",", Collections.nCopies(ids.size(), "?"))
            + ")",
        Long.class,
        args.toArray());
  }

  public void requireView(String actor, Post p) {
    if (p.hidden || p.deletedAt != null) throw missing();
    if (actor.equals(p.authorId)) return;
    var d = members.policy(actor, List.of(p.authorId)).get(p.authorId);
    if (!d.visible() || (p.visibility == Post.Visibility.CONNECTIONS && !d.connected()))
      throw missing();
  }

  private PostView view(Post p) {
    return view(
        p,
        db.queryForList(
            "SELECT media_id FROM media_references WHERE resource_id=? ORDER BY position",
            String.class,
            p.id));
  }

  private PostView view(Post p, List<String> media) {
    return new PostView(
        p.id, p.authorId, p.body, p.createdAt, p.updatedAt, p.version, p.visibility, media);
  }

  private Map<String, List<String>> mediaReferences(List<String> ids) {
    var result = new HashMap<String, List<String>>();
    if (ids.isEmpty()) return result;
    db.query(
        "SELECT resource_id,media_id FROM media_references WHERE resource_id IN ("
            + String.join(",", Collections.nCopies(ids.size(), "?"))
            + ") ORDER BY resource_id,position",
        (org.springframework.jdbc.core.RowCallbackHandler)
            r -> result.computeIfAbsent(r.getString(1), k -> new ArrayList<>()).add(r.getString(2)),
        ids.toArray());
    return result;
  }

  private CommentView view(Comment c) {
    return new CommentView(c.id, c.postId, c.authorId, c.body, c.createdAt);
  }
}
