package dev.network.content.post;

import dev.network.content.feed.*;
import dev.network.web.Pages;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DiscoveryContentService {
  private static final int BATCH = 100, SCAN_LIMIT = 500;
  private final JdbcTemplate db;
  private final PostRepository posts;
  private final ContentService content;
  private final MemberClient members;
  private final Clock clock;
  private final MeterRegistry metrics;

  public DiscoveryContentService(
      JdbcTemplate db,
      PostRepository posts,
      ContentService content,
      MemberClient members,
      Clock clock,
      MeterRegistry metrics) {
    this.db = db;
    this.posts = posts;
    this.content = content;
    this.members = members;
    this.clock = clock;
    this.metrics = metrics;
  }

  public record Saved(ContentService.PostView post, Instant savedAt) {}

  private record Candidate(
      String id, String author, Post.Visibility visibility, boolean unavailable, Instant time) {}

  @Transactional
  public void save(String actor, String id) {
    var p =
        posts
            .lock(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
    content.requireView(actor, p);
    db.update(
        "INSERT INTO saved_posts(member_id,post_id,saved_at) SELECT ?,?,? FROM dual WHERE NOT"
            + " EXISTS (SELECT 1 FROM saved_posts WHERE member_id=? AND post_id=?)",
        actor,
        id,
        Timestamp.from(clock.instant()),
        actor,
        id);
  }

  @Transactional
  public void unsave(String actor, String id) {
    UUID.fromString(id);
    db.update("DELETE FROM saved_posts WHERE member_id=? AND post_id=?", actor, id);
  }

  @Transactional(readOnly = true)
  public Pages.Slice<ContentService.PostView> feed(String actor, String cursor, int size) {
    var followed = new HashSet<>(members.followed(actor));
    var authors = new TreeSet<>(members.accepted(actor));
    authors.addAll(followed);
    authors.add(actor);
    if (authors.size() > 1001)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Relationship bound exceeded");
    var result = scan(actor, authors, followed, cursor, size, false);
    return new Pages.Slice<>(
        result.items().stream().map(Saved::post).toList(), result.nextCursor());
  }

  @Transactional(readOnly = true)
  public Pages.Slice<Saved> saved(String actor, String cursor, int size) {
    return scan(actor, Set.of(), Set.of(), cursor, size, true);
  }

  private Pages.Slice<Saved> scan(
      String actor,
      Set<String> authors,
      Set<String> followed,
      String cursor,
      int size,
      boolean saved) {
    Pages.size(size);
    var position = Cursor.parse(cursor, clock);
    var selected = new ArrayList<Candidate>();
    boolean more = false;
    int scanned = 0;
    while (scanned < SCAN_LIMIT && selected.size() < size) {
      var args = new ArrayList<Object>();
      String from = "posts p", where = "1=1", time = "p.created_at";
      if (saved) {
        from += " JOIN saved_posts s ON s.post_id=p.id";
        where = "s.member_id=?";
        args.add(actor);
        time = "s.saved_at";
      } else {
        var ids = new ArrayList<>(authors);
        var groups = new ArrayList<String>();
        for (int i = 0; i < ids.size(); i += 500) {
          var batch = ids.subList(i, Math.min(i + 500, ids.size()));
          groups.add(
              "p.author_id IN (" + String.join(",", Collections.nCopies(batch.size(), "?")) + ")");
          args.addAll(batch);
        }
        where = "(" + String.join(" OR ", groups) + ")";
      }
      where += " AND (" + time + "<? OR (" + time + "=? AND p.id<?))";
      args.add(Timestamp.from(position.time()));
      args.add(Timestamp.from(position.time()));
      args.add(position.id());
      var rows =
          db.query(
              "SELECT p.id,p.author_id,p.visibility,p.hidden,p.deleted_at,"
                  + time
                  + " AS sort_at FROM "
                  + from
                  + " WHERE "
                  + where
                  + " ORDER BY "
                  + time
                  + " DESC,p.id DESC FETCH FIRST 100 ROWS ONLY",
              (r, n) ->
                  new Candidate(
                      r.getString(1),
                      r.getString(2),
                      Post.Visibility.valueOf(r.getString(3)),
                      r.getInt(4) == 1 || r.getTimestamp(5) != null,
                      r.getTimestamp(6).toInstant()),
              args.toArray());
      var targets =
          rows.stream()
              .filter(r -> !r.unavailable() && !r.author().equals(actor))
              .map(Candidate::author)
              .distinct()
              .toList();
      var policy = members.policy(actor, targets);
      int consumed = 0;
      for (var r : rows) {
        consumed++;
        scanned++;
        position = new Cursor(r.time(), r.id());
        var d = policy.get(r.author());
        boolean allowed =
            !r.unavailable()
                && (r.author().equals(actor)
                    || (d != null
                        && d.visible()
                        && (r.visibility() == Post.Visibility.MEMBERS || d.connected())
                        && (saved || d.connected() || followed.contains(r.author()))));
        if (allowed) selected.add(r);
        if (selected.size() == size) break;
      }
      more = consumed < rows.size() || rows.size() == BATCH;
      if (!more || selected.size() == size) break;
    }
    metrics
        .counter("network.discovery.candidates", "query", saved ? "saved_posts" : "feed")
        .increment(scanned);
    var ids = selected.stream().map(Candidate::id).toList();
    var hydrated = new HashMap<String, Post>();
    posts.findAllById(ids).forEach(p -> hydrated.put(p.id, p));
    var media = content.mediaReferences(ids);
    var result =
        selected.stream()
            .filter(
                r -> {
                  var p = hydrated.get(r.id());
                  return p != null
                      && !p.hidden
                      && p.deletedAt == null
                      && p.visibility == r.visibility();
                })
            .map(
                r ->
                    new Saved(
                        content.view(hydrated.get(r.id()), media.getOrDefault(r.id(), List.of())),
                        r.time()))
            .toList();
    metrics
        .counter("network.discovery.returned", "query", saved ? "saved_posts" : "feed")
        .increment(result.size());
    return new Pages.Slice<>(result, more ? position.encode() : null);
  }
}
