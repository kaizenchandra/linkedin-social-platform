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
    private static final RowMapper<Pair> PAIR =
            (r, n) ->
                    new Pair(
                            r.getString("id"),
                            r.getString("low_id"),
                            r.getString("high_id"),
                            r.getLong("last_sequence"),
                            r.getLong("low_read"),
                            r.getLong("high_read"),
                            r.getTimestamp("created_at").toInstant(),
                            r.getLong("low_read_version"),
                            r.getLong("high_read_version"));
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
    private final JdbcTemplate db;
    private final ServiceHttp http;
    private final EventWriter events;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final String memberUrl;
    private final dev.network.web.stream.DurableStream stream;

    public MessagingService(
            JdbcTemplate db,
            dev.network.web.stream.DurableStream stream,
            ServiceHttp http,
            EventWriter events,
            Clock clock,
            org.springframework.transaction.PlatformTransactionManager tm,
            @Value("${MEMBER_URL:http://localhost:8081}") String memberUrl) {
        this.db = db;
        this.stream = stream;
        this.http = http;
        this.events = events;
        this.clock = clock;
        tx = new TransactionTemplate(tm);
        this.memberUrl = memberUrl;
    }

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
                    status -> {
                        String id = UUID.randomUUID().toString();
                        db.update(
                                "INSERT INTO"
                                        + " conversations(id,low_id,high_id,last_sequence,low_read,high_read,created_at)"
                                        + " VALUES(?,?,?,0,0,0,?)",
                                id,
                                low,
                                high,
                                java.sql.Timestamp.from(now()));
                        for (String owner : List.of(low, high))
                            db.update(
                                    "INSERT INTO conversation_preferences(conversation_id,member_id) VALUES(?,?)",
                                    id,
                                    owner);
                        stream.append(
                                List.of(
                                        new dev.network.web.stream.DurableStream.Update(
                                                low, "conversation.created", id, 0),
                                        new dev.network.web.stream.DurableStream.Update(
                                                high, "conversation.created", id, 0)));
                    });
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
        var updates = new ArrayList<dev.network.web.stream.DurableStream.Update>();
        for (String owner : List.of(p.low, p.high)) {
            int changed =
                    db.update(
                            "UPDATE conversation_preferences SET archived=0,version=version+1 WHERE"
                                    + " conversation_id=? AND member_id=? AND archived=1",
                            id,
                            owner);
            if (changed == 1)
                updates.add(
                        new dev.network.web.stream.DurableStream.Update(
                                owner, "conversation.preferences", id, preference(id, owner).version()));
            updates.add(
                    new dev.network.web.stream.DurableStream.Update(owner, "message.created", id, sequence));
        }
        stream.append(updates);
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
        if (position > p.read(actor)) {
            db.update(
                    "UPDATE conversations SET "
                            + column
                            + "=?,"
                            + column
                            + "_version="
                            + column
                            + "_version+1 WHERE id=?",
                    position,
                    id);
            stream.append(
                    List.of(
                            new dev.network.web.stream.DurableStream.Update(
                                    actor,
                                    "conversation.read",
                                    id,
                                    (actor.equals(p.low) ? p.lowVersion : p.highVersion) + 1)));
        }
        return view(pair(actor, id, false), actor);
    }

    @Transactional(readOnly = true)
    public Conversation get(String actor, String id) {
        return view(pair(actor, id, false), actor);
    }

    @Transactional(readOnly = true)
    public Pages.Slice<Conversation> list(String actor, String cursor, int size) {
        return list(actor, cursor, size, false, false);
    }

    @Transactional(readOnly = true)
    public Pages.Slice<Conversation> list(
            String actor, String cursor, int size, boolean archived, boolean all) {
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
                        "SELECT c.*, pref.muted,pref.archived,pref.version AS preference_version, (SELECT"
                                + " COUNT(*) FROM messages m WHERE m.conversation_id=c.id AND m.sender_id<>? AND"
                                + " m.sequence_number>CASE WHEN c.low_id=? THEN c.low_read ELSE c.high_read END)"
                                + " unread FROM conversations c JOIN conversation_preferences pref ON"
                                + " pref.conversation_id=c.id AND pref.member_id=? WHERE (low_id=? OR high_id=?)"
                                + " AND (c.created_at<? OR (c.created_at=? AND c.id<?))"
                                + (all ? "" : " AND pref.archived=" + (archived ? 1 : 0))
                                + " ORDER BY c.created_at DESC,c.id DESC"
                                + " FETCH FIRST ? ROWS ONLY",
                        (r, n) -> {
                            var p = PAIR.mapRow(r, n);
                            return new Conversation(
                                    p.id,
                                    p.low,
                                    p.high,
                                    p.last,
                                    p.read(actor),
                                    r.getLong("unread"),
                                    p.created,
                                    actor.equals(p.low) ? p.lowVersion : p.highVersion,
                                    r.getInt("muted") == 1,
                                    r.getInt("archived") == 1,
                                    r.getLong("preference_version"));
                        },
                        actor,
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
        var pref = preference(p.id, actor);
        return new Conversation(
                p.id,
                p.low,
                p.high,
                p.last,
                p.read(actor),
                unread,
                p.created,
                actor.equals(p.low) ? p.lowVersion : p.highVersion,
                pref.muted(),
                pref.archived(),
                pref.version());
    }

    private Preference preference(String id, String actor) {
        return db.queryForObject(
                "SELECT muted,archived,version FROM conversation_preferences WHERE conversation_id=? AND"
                        + " member_id=?",
                (r, n) -> new Preference(r.getInt(1) == 1, r.getInt(2) == 1, r.getLong(3)),
                id,
                actor);
    }

    @Transactional
    public Conversation preferences(String actor, String id, Boolean muted, Boolean archived) {
        if (muted == null && archived == null)
            throw new IllegalArgumentException("At least one preference required");
        var p = pair(actor, id, true);
        var old = preference(id, actor);
        boolean m = muted == null ? old.muted() : muted,
                a = archived == null ? old.archived() : archived;
        if (m != old.muted() || a != old.archived()) {
            db.update(
                    "UPDATE conversation_preferences SET muted=?,archived=?,version=version+1 WHERE"
                            + " conversation_id=? AND member_id=?",
                    m ? 1 : 0,
                    a ? 1 : 0,
                    id,
                    actor);
            stream.append(
                    List.of(
                            new dev.network.web.stream.DurableStream.Update(
                                    actor, "conversation.preferences", id, old.version() + 1)));
        }
        return view(p, actor);
    }

    @Transactional(readOnly = true)
    public boolean notificationAllowed(String actor, String id) {
        var rows = db.query("SELECT * FROM conversations WHERE id=?", PAIR, id);
        return !rows.isEmpty() && rows.getFirst().participant(actor) && !preference(id, actor).muted();
    }

    @Transactional(readOnly = true)
    public long unread(String actor) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM messages m JOIN conversations c ON c.id=m.conversation_id WHERE"
                        + " (c.low_id=? OR c.high_id=?) AND m.sender_id<>? AND m.sequence_number>CASE WHEN"
                        + " c.low_id=? THEN c.low_read ELSE c.high_read END",
                Long.class,
                actor,
                actor,
                actor,
                actor);
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

    public record Policy(String memberId, boolean visible, boolean connected) {
    }

    public record Conversation(
            String id,
            String lowId,
            String highId,
            long lastSequence,
            long readPosition,
            long unreadCount,
            Instant createdAt,
            long readVersion,
            boolean muted,
            boolean archived,
            long preferenceVersion) {
    }

    public record Message(
            String id,
            String conversationId,
            String senderId,
            String clientMessageId,
            long sequence,
            String body,
            Instant createdAt) {
    }

    private record Pair(
            String id,
            String low,
            String high,
            long last,
            long lowRead,
            long highRead,
            Instant created,
            long lowVersion,
            long highVersion) {
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

    public record Preference(boolean muted, boolean archived, long version) {
    }
}
