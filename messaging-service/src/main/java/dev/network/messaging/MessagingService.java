package dev.network.messaging;

import dev.network.messaging.events.EventWriter;
import dev.network.web.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MessagingService {
  private final JdbcTemplate db;
  private final ServiceHttp http;
  private final EventWriter events;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final String memberUrl;

  public MessagingService(
      JdbcTemplate db,
      ServiceHttp http,
      EventWriter events,
      Clock clock,
      org.springframework.transaction.PlatformTransactionManager tm,
      @Value("${MEMBER_URL:http://localhost:8081}") String memberUrl) {
    this.db = db;
    this.http = http;
    this.events = events;
    this.clock = clock;
    tx = new TransactionTemplate(tm);
    this.memberUrl = memberUrl;
  }

  public record Policy(String memberId, boolean visible, boolean connected) {}

  public record Conversation(
      String id,
      String lowId,
      String highId,
      long lastSequence,
      long readPosition,
      long unreadCount,
      Instant createdAt) {}

  public record Message(
      String id,
      String conversationId,
      String senderId,
      String clientMessageId,
      long sequence,
      String body,
      Instant createdAt) {}

  private record Pair(
      String id, String low, String high, long last, long lowRead, long highRead, Instant created) {
    boolean participant(String actor) {
      return actor.equals(low) || actor.equals(high);
    }

    long read(String actor) {
      return actor.equals(low) ? lowRead : highRead;
    }

    String other(String actor) {
      return actor.equals(low) ? high : low;
    }
  }

  private static final RowMapper<Pair> PAIR =
      (r, n) ->
          new Pair(
              r.getString("id"),
              r.getString("low_id"),
              r.getString("high_id"),
              r.getLong("last_sequence"),
              r.getLong("low_read"),
              r.getLong("high_read"),
              r.getTimestamp("created_at").toInstant());
  private static final RowMapper<Message> MESSAGE =
      (r, n) ->
          new Message(
              r.getString("id"),
              r.getString("conversation_id"),
              r.getString("sender_id"),
              r.getString("client_message_id"),
              r.getLong("sequence_number"),
              r.getString("body"),
              r.getTimestamp("created_at").toInstant());

  private Instant now() {
    return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation or message not found");
  }

  private void connected(String actor, String target) {
    var result =
        http.post(
            memberUrl + "/internal/v1/policy",
            Map.of("actorId", actor, "memberIds", List.of(target)),
            Policy[].class);
    if (result == null || result.length != 1 || !result[0].memberId().equals(target))
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Current relationship unavailable");
    if (!result[0].visible() || !result[0].connected())
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "An accepted unblocked connection is required");
  }

  private Pair pair(String actor, String id, boolean lock) {
    UUID.fromString(id);
    var rows =
        db.query("SELECT * FROM conversations WHERE id=?" + (lock ? " FOR UPDATE" : ""), PAIR, id);
    if (rows.isEmpty() || !rows.getFirst().participant(actor)) throw missing();
    return rows.getFirst();
  }

  public Conversation start(String actor, String target) {
    UUID.fromString(target);
    if (actor.equals(target))
      throw new IllegalArgumentException("Two distinct participants required");
    connected(actor, target);
    String low = actor.compareTo(target) < 0 ? actor : target,
        high = actor.compareTo(target) < 0 ? target : actor;
    var existing =
        db.query("SELECT * FROM conversations WHERE low_id=? AND high_id=?", PAIR, low, high);
    if (!existing.isEmpty()) return view(existing.getFirst(), actor);
    try {
      tx.executeWithoutResult(
          s ->
              db.update(
                  "INSERT INTO"
                      + " conversations(id,low_id,high_id,last_sequence,low_read,high_read,created_at)"
                      + " VALUES(?,?,?,0,0,0,?)",
                  UUID.randomUUID().toString(),
                  low,
                  high,
                  java.sql.Timestamp.from(now())));
    } catch (DataIntegrityViolationException race) {
      if (db.queryForObject(
              "SELECT COUNT(*) FROM conversations WHERE low_id=? AND high_id=?",
              Long.class,
              low,
              high)
          != 1) throw race;
    }
    return view(
        db.query("SELECT * FROM conversations WHERE low_id=? AND high_id=?", PAIR, low, high)
            .getFirst(),
        actor);
  }

  @Transactional
  public Message send(String actor, String id, String clientId, String body) {
    UUID.fromString(clientId);
    if (body == null || body.isBlank() || body.length() > 4000)
      throw new IllegalArgumentException("Invalid message");
    var p = pair(actor, id, true);
    var existing =
        db.query(
            "SELECT * FROM messages WHERE conversation_id=? AND sender_id=? AND"
                + " client_message_id=?",
            MESSAGE,
            id,
            actor,
            clientId);
    if (!existing.isEmpty()) {
      if (!existing.getFirst().body().equals(body))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Client message ID was used for different content");
      return existing.getFirst();
    }
    connected(actor, p.other(actor));
    long recent =
        db.queryForObject(
            "SELECT COUNT(*) FROM messages WHERE conversation_id=? AND sender_id=? AND"
                + " created_at>=?",
            Long.class,
            id,
            actor,
            java.sql.Timestamp.from(now().minusSeconds(60)));
    if (recent >= 60)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Conversation send limit reached");
    long sequence = p.last + 1;
    var message =
        new Message(UUID.randomUUID().toString(), id, actor, clientId, sequence, body, now());
    db.update("UPDATE conversations SET last_sequence=? WHERE id=?", sequence, id);
    db.update(
        "INSERT INTO"
            + " messages(id,conversation_id,sender_id,client_message_id,sequence_number,body,created_at)"
            + " VALUES(?,?,?,?,?,?,?)",
        message.id(),
        id,
        actor,
        clientId,
        sequence,
        body,
        java.sql.Timestamp.from(message.createdAt()));
    events.write("message.sent", id, sequence, actor, p.other(actor));
    return message;
  }

  @Transactional(readOnly = true)
  public Pages.Slice<Message> history(String actor, String id, String cursor, int size) {
    pair(actor, id, false);
    long after = decode(cursor, id);
    int limit = Pages.size(size);
    var rows =
        db.query(
            "SELECT * FROM messages WHERE conversation_id=? AND sequence_number>? ORDER BY"
                + " sequence_number FETCH FIRST ? ROWS ONLY",
            MESSAGE,
            id,
            after,
            limit);
    long last = rows.isEmpty() ? after : rows.getLast().sequence();
    return new Pages.Slice<>(rows, encode(id, last));
  }

  @Transactional
  public Conversation read(String actor, String id, String messageId) {
    var p = pair(actor, id, true);
    UUID.fromString(messageId);
    var rows =
        db.queryForList(
            "SELECT sequence_number FROM messages WHERE conversation_id=? AND id=?",
            Long.class,
            id,
            messageId);
    if (rows.isEmpty()) throw missing();
    long position = Math.max(p.read(actor), rows.getFirst());
    String column = actor.equals(p.low) ? "low_read" : "high_read";
    db.update("UPDATE conversations SET " + column + "=? WHERE id=?", position, id);
    return view(pair(actor, id, false), actor);
  }

  @Transactional(readOnly = true)
  public Conversation get(String actor, String id) {
    return view(pair(actor, id, false), actor);
  }

  @Transactional(readOnly = true)
  public Pages.Slice<Conversation> list(String actor, String cursor, int size) {
    int limit = Pages.size(size);
    Instant before = now().plusSeconds(1);
    String last = "~";
    if (cursor != null) {
      try {
        String[] parts =
            new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                .split("\\|", -1);
        if (parts.length != 3 || !parts[0].equals(actor)) throw new IllegalArgumentException();
        before = Instant.parse(parts[1]);
        last = parts[2];
        UUID.fromString(last);
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid cursor");
      }
    }
    var rows =
        db.query(
            "SELECT c.*, (SELECT COUNT(*) FROM messages m WHERE m.conversation_id=c.id AND"
                + " m.sender_id<>? AND m.sequence_number>CASE WHEN c.low_id=? THEN c.low_read ELSE"
                + " c.high_read END) unread FROM conversations c WHERE (low_id=? OR high_id=?) AND"
                + " (created_at<? OR (created_at=? AND id<?)) ORDER BY created_at DESC,id DESC"
                + " FETCH FIRST ? ROWS ONLY",
            (r, n) -> {
              var p = PAIR.mapRow(r, n);
              return new Conversation(
                  p.id, p.low, p.high, p.last, p.read(actor), r.getLong("unread"), p.created);
            },
            actor,
            actor,
            actor,
            actor,
            java.sql.Timestamp.from(before),
            java.sql.Timestamp.from(before),
            last,
            limit + 1);
    boolean more = rows.size() > limit;
    var items = rows.stream().limit(limit).toList();
    String next = null;
    if (more) {
      var p = items.getLast();
      next =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  (actor + "|" + p.createdAt() + "|" + p.id()).getBytes(StandardCharsets.UTF_8));
    }
    return new Pages.Slice<>(items, next);
  }

  private Conversation view(Pair p, String actor) {
    long unread =
        db.queryForObject(
            "SELECT COUNT(*) FROM messages WHERE conversation_id=? AND sender_id<>? AND"
                + " sequence_number>?",
            Long.class,
            p.id,
            actor,
            p.read(actor));
    return new Conversation(p.id, p.low, p.high, p.last, p.read(actor), unread, p.created);
  }

  private String encode(String id, long sequence) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((id + "|" + sequence).getBytes(StandardCharsets.UTF_8));
  }

  private long decode(String cursor, String id) {
    if (cursor == null) return 0;
    try {
      if (cursor.length() > 200) throw new IllegalArgumentException();
      String[] p =
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
              .split("\\|", -1);
      long n = Long.parseLong(p[1]);
      if (p.length != 2 || !p[0].equals(id) || n < 0) throw new IllegalArgumentException();
      return n;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid message cursor");
    }
  }
}
