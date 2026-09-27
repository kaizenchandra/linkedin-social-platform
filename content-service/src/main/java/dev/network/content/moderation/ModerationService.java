package dev.network.content.moderation;

import dev.network.content.events.EventWriter;
import dev.network.content.post.*;
import dev.network.web.Pages;

import java.time.*;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModerationService {
    private static final RowMapper<Report> REPORT =
            (r, n) ->
                    new Report(
                            r.getString("id"),
                            TargetType.valueOf(r.getString("target_type")),
                            r.getString("target_id"),
                            Reason.valueOf(r.getString("reason")),
                            r.getString("explanation"),
                            r.getString("state"),
                            r.getTimestamp("created_at").toInstant());
    private final dev.network.content.feed.MemberClient members;
    private final JdbcTemplate db;
    private final ContentService content;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final EventWriter events;
    private final Clock clock;
    public ModerationService(
            JdbcTemplate db,
            ContentService content,
            PostRepository posts,
            CommentRepository comments,
            EventWriter events,
            Clock clock,
            dev.network.content.feed.MemberClient members) {
        this.members = members;
        this.db = db;
        this.content = content;
        this.posts = posts;
        this.comments = comments;
        this.events = events;
        this.clock = clock;
    }

    private Instant now() {
        return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Content or report not found");
    }

    private Report report(String id) {
        UUID.fromString(id);
        var rows = db.query("SELECT * FROM content_reports WHERE id=?", REPORT, id);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    @Transactional
    public Report submit(
            String actor, TargetType type, String id, Reason reason, String explanation) {
        UUID.fromString(id);
        if (type == null
                || reason == null
                || explanation != null && explanation.length() > 1000
                || reason == Reason.OTHER && (explanation == null || explanation.isBlank()))
            throw new IllegalArgumentException("Invalid report");
        if (type == TargetType.POST) {
            posts.lock(id).orElseThrow(this::missing);
            content.detail(actor, id);
        } else {
            var c = comments.findById(id).orElseThrow(this::missing);
            posts.lock(c.postId).orElseThrow(this::missing);
            var flags = db.queryForMap("SELECT hidden,deleted_at FROM comments WHERE id=?", id);
            if (((Number) flags.get("HIDDEN")).intValue() != 0 || flags.get("DELETED_AT") != null)
                throw missing();
            content.detail(actor, c.postId);
            if (!actor.equals(c.authorId)
                    && !members.policy(actor, List.of(c.authorId)).get(c.authorId).visible()) throw missing();
        }
        var existing =
                db.query(
                        "SELECT * FROM content_reports WHERE reporter_id=? AND target_type=? AND target_id=?"
                                + " AND state='OPEN'",
                        REPORT,
                        actor,
                        type.name(),
                        id);
        if (!existing.isEmpty()) return existing.getFirst();
        String reportId = UUID.randomUUID().toString();
        db.update(
                "INSERT INTO"
                        + " content_reports(id,reporter_id,target_type,target_id,reason,explanation,state,created_at)"
                        + " VALUES(?,?,?,?,?,?,'OPEN',?)",
                reportId,
                actor,
                type.name(),
                id,
                reason.name(),
                explanation,
                java.sql.Timestamp.from(now()));
        return report(reportId);
    }

    @Transactional(readOnly = true)
    public List<Report> own(String actor, int page, int size) {
        return db.query(
                "SELECT * FROM content_reports WHERE reporter_id=? ORDER BY created_at DESC,id DESC OFFSET"
                        + " ? ROWS FETCH NEXT ? ROWS ONLY",
                REPORT,
                actor,
                Pages.page(page) * Pages.size(size),
                Pages.size(size));
    }

    @PreAuthorize("hasRole('moderator')")
    @Transactional(readOnly = true)
    public Pages.Slice<Report> queue(String cursor, int size) {
        var position = dev.network.content.feed.Cursor.parse(cursor, clock);
        int limit = Pages.size(size);
        var rows =
                db.query(
                        "SELECT * FROM content_reports WHERE state='OPEN' AND (created_at<? OR (created_at=?"
                                + " AND id<?)) ORDER BY created_at DESC,id DESC FETCH FIRST ? ROWS ONLY",
                        REPORT,
                        java.sql.Timestamp.from(position.time()),
                        java.sql.Timestamp.from(position.time()),
                        position.id(),
                        limit + 1);
        var items = rows.stream().limit(limit).toList();
        String next =
                rows.size() > limit
                        ? new dev.network.content.feed.Cursor(items.getLast().createdAt(), items.getLast().id())
                        .encode()
                        : null;
        return new Pages.Slice<>(items, next);
    }

    private void audit(String actor, Report r, String action, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000)
            throw new IllegalArgumentException("A bounded audit reason is required");
        db.update(
                "INSERT INTO"
                        + " moderation_audit(id,actor_id,report_id,target_type,target_id,action,reason,occurred_at)"
                        + " VALUES(?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),
                actor,
                r.id(),
                r.targetType().name(),
                r.targetId(),
                action,
                reason,
                java.sql.Timestamp.from(now()));
    }

    @PreAuthorize("hasRole('moderator')")
    @Transactional
    public Inspection inspect(String actor, String reportId, String reason) {
        var r = report(reportId);
        Inspection result;
        if (r.targetType() == TargetType.POST) {
            var p = posts.findById(r.targetId()).orElseThrow(this::missing);
            result =
                    new Inspection(
                            r, p.authorId, p.deletedAt == null ? p.body : null, p.hidden, p.deletedAt != null);
        } else {
            var c = comments.findById(r.targetId()).orElseThrow(this::missing);
            var p = posts.findById(c.postId).orElseThrow(this::missing);
            boolean deleted = c.deletedAt != null || p.deletedAt != null;
            result = new Inspection(r, c.authorId, deleted ? null : c.body, c.hidden, deleted);
        }
        audit(actor, r, "INSPECT", reason);
        return result;
    }

    @PreAuthorize("hasRole('moderator')")
    @Transactional
    public Report act(String actor, String reportId, Action action, String reason) {
        var r = report(reportId);
        if (action == null) throw new IllegalArgumentException();
        if (action == Action.DISMISS) {
            db.update("UPDATE content_reports SET state='DISMISSED' WHERE id=?", reportId);
            audit(actor, r, "DISMISS", reason);
            return report(reportId);
        }
        boolean hidden = action == Action.HIDE, changed;
        String author;
        if (r.targetType() == TargetType.POST) {
            var p = posts.lock(r.targetId()).orElseThrow(this::missing);
            if (p.deletedAt != null)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Author-deleted content cannot be restored or moderated");
            author = p.authorId;
            changed = p.hidden != hidden;
            p.hidden = hidden;
            posts.flush();
        } else {
            var c = comments.findById(r.targetId()).orElseThrow(this::missing);
            var p = posts.lock(c.postId).orElseThrow(this::missing);
            // Refresh after parent lock so concurrent moderation cannot use stale flags.
            var current = db.queryForMap("SELECT hidden,deleted_at FROM comments WHERE id=?", c.id);
            if (current.get("DELETED_AT") != null || p.deletedAt != null)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Author-deleted content cannot be restored or moderated");
            author = c.authorId;
            changed = ((Number) current.get("HIDDEN")).intValue() != (hidden ? 1 : 0);
            c.hidden = hidden;
            comments.flush();
        }
        audit(actor, r, action.name(), reason);
        if (changed)
            events.write(
                    hidden ? "moderation.hidden" : "moderation.restored", r.targetId(), 0, actor, author);
        return report(reportId);
    }

    @PreAuthorize("hasRole('moderator')")
    @Transactional(readOnly = true)
    public List<Audit> history(String reportId, int page, int size) {
        report(reportId);
        return db.query(
                "SELECT * FROM moderation_audit WHERE report_id=? ORDER BY occurred_at,id OFFSET ? ROWS"
                        + " FETCH NEXT ? ROWS ONLY",
                (r, n) ->
                        new Audit(
                                r.getString("id"),
                                r.getString("actor_id"),
                                r.getString("action"),
                                r.getString("target_type"),
                                r.getString("target_id"),
                                r.getString("reason"),
                                r.getTimestamp("occurred_at").toInstant()),
                reportId,
                Pages.page(page) * Pages.size(size),
                Pages.size(size));
    }

    public enum TargetType {
        POST,
        COMMENT
    }

    public enum Reason {
        SPAM,
        HARASSMENT,
        INAPPROPRIATE,
        OTHER
    }

    public enum Action {
        HIDE,
        RESTORE,
        DISMISS
    }

    public record Report(
            String id,
            TargetType targetType,
            String targetId,
            Reason reason,
            String explanation,
            String state,
            Instant createdAt) {
    }

    public record Inspection(
            Report report, String authorId, String body, boolean hidden, boolean deleted) {
    }

    public record Audit(
            String id,
            String actorId,
            String action,
            String targetType,
            String targetId,
            String reason,
            Instant occurredAt) {
    }
}
