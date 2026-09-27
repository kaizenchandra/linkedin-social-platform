package dev.network.web.stream;

import io.micrometer.core.instrument.MeterRegistry;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Technical owner-scoped history. Each application owns its own tables and business events.
 */
public final class DurableStream {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String kind;
    private final MeterRegistry metrics;

    public DurableStream(
            JdbcTemplate db,
            PlatformTransactionManager tm,
            Clock clock,
            String kind,
            MeterRegistry metrics) {
        this.db = new JdbcTemplate(java.util.Objects.requireNonNull(db.getDataSource()));
        this.db.setQueryTimeout(3);
        this.db.setFetchSize(50);
        this.clock = clock;
        this.kind = kind;
        this.metrics = metrics;
        tx = new TransactionTemplate(tm);
        tx.setTimeout(3);
    }

    private Head lock(String owner) {
        UUID.fromString(owner);
        try {
            db.update(
                    "INSERT INTO stream_heads(owner_id) SELECT ? FROM dual WHERE NOT EXISTS(SELECT 1 FROM"
                            + " stream_heads WHERE owner_id=?)",
                    owner,
                    owner);
        } catch (DuplicateKeyException concurrentInitialization) {
            // Oracle rolls back the losing statement, not the surrounding transaction.
        }
        return db.queryForObject(
                "SELECT last_position,retained_floor FROM stream_heads WHERE owner_id=? FOR UPDATE WAIT 2",
                (r, n) -> new Head(r.getLong(1), r.getLong(2)),
                owner);
    }

    public void append(List<Update> updates) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Replay writes require the business transaction");
        var positions = new TreeMap<String, Long>();
        updates.stream()
                .map(Update::owner)
                .distinct()
                .sorted()
                .forEach(owner -> positions.put(owner, lock(owner).last()));
        for (var update : updates) {
            UUID.fromString(update.resource());
            if (!update.type().matches("[a-z.]{1,60}") || update.version() < 0)
                throw new IllegalArgumentException();
            long next = Math.addExact(positions.get(update.owner()), 1);
            positions.put(update.owner(), next);
            db.update(
                    "INSERT INTO"
                            + " stream_events(owner_id,position,event_id,event_type,resource_id,resource_version,occurred_at)"
                            + " VALUES(?,?,?,?,?,?,?)",
                    update.owner(),
                    next,
                    UUID.randomUUID().toString(),
                    update.type(),
                    update.resource(),
                    update.version(),
                    Timestamp.from(clock.instant()));
        }
        positions.forEach(
                (owner, last) ->
                        db.update("UPDATE stream_heads SET last_position=? WHERE owner_id=?", last, owner));
        if (!updates.isEmpty())
            TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            metrics.counter("network.stream.events.persisted").increment(updates.size());
                        }
                    });
    }

    public String boundary(String owner) {
        return tx.execute(s -> encode(owner, lock(owner).last()));
    }

    public long position(String owner, String cursor) {
        try {
            if (cursor == null || cursor.length() > 240) throw new IllegalArgumentException();
            String[] p =
                    new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                            .split("\\|", -1);
            if (p.length != 4 || !p[0].equals("1") || !p[1].equals(kind) || !p[2].equals(owner))
                throw new IllegalArgumentException();
            long position = Long.parseLong(p[3]);
            if (position < 0) throw new IllegalArgumentException();
            return position;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid stream cursor");
        }
    }

    private String encode(String owner, long position) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        ("1|" + kind + "|" + owner + "|" + position).getBytes(StandardCharsets.UTF_8));
    }

    public List<Event> replay(String owner, String cursor, int size) {
        if (size < 1 || size > 50) throw new IllegalArgumentException("Replay limit1..50");
        long after = position(owner, cursor);
        metrics.counter("network.stream.replay.requests").increment();
        return tx.execute(
                s -> {
                    Head head = lock(owner);
                    if (after < head.floor()) {
                        metrics.counter("network.stream.resets").increment();
                        throw new ResponseStatusException(
                                HttpStatus.GONE, "Replay expired; fetch synchronization state before reconnecting");
                    }
                    if (after > head.last())
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Cursor is ahead of durable history");
                    return db.query(
                            "SELECT * FROM stream_events WHERE owner_id=? AND position>? ORDER BY position FETCH"
                                    + " FIRST ? ROWS ONLY",
                            (r, n) ->
                                    new Event(
                                            r.getString("event_id"),
                                            r.getString("event_type"),
                                            1,
                                            r.getTimestamp("occurred_at").toInstant(),
                                            r.getString("resource_id"),
                                            r.getLong("resource_version"),
                                            encode(owner, r.getLong("position"))),
                            owner,
                            after,
                            size);
                });
    }

    public int pruneOwner(String owner, Instant cutoff) {
        return tx.execute(
                s -> {
                    lock(owner);
                    var rows =
                            db.query(
                                    "SELECT position,occurred_at FROM stream_events WHERE owner_id=? ORDER BY"
                                            + " position FETCH FIRST 500 ROWS ONLY",
                                    (r, n) -> Map.entry(r.getLong(1), r.getTimestamp(2).toInstant()),
                                    owner);
                    long floor = 0;
                    int count = 0;
                    for (var row : rows) {
                        if (!row.getValue().isBefore(cutoff)) break;
                        floor = row.getKey();
                        count++;
                    }
                    if (count > 0) {
                        db.update("DELETE FROM stream_events WHERE owner_id=? AND position<=?", owner, floor);
                        db.update("UPDATE stream_heads SET retained_floor=? WHERE owner_id=?", floor, owner);
                    }
                    return count;
                });
    }

    public void cleanup(Duration retention) {
        Instant cutoff = clock.instant().minus(retention);
        var owners =
                db.queryForList(
                        "SELECT owner_id FROM stream_events GROUP BY owner_id HAVING MIN(occurred_at)<? ORDER"
                                + " BY MIN(occurred_at),owner_id FETCH FIRST 25 ROWS ONLY",
                        String.class,
                        Timestamp.from(cutoff));
        for (String owner : owners)
            metrics.counter("network.stream.cleanup.events").increment(pruneOwner(owner, cutoff));
    }

    public record Update(String owner, String type, String resource, long version) {
    }

    public record Event(
            String eventId,
            String eventType,
            int schemaVersion,
            Instant occurredAt,
            String resourceId,
            long resourceVersion,
            String cursor) {
    }

    private record Head(long last, long floor) {
    }
}
