package dev.network.content.media;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/media")
public class AttachmentController {
    private final JdbcTemplate db;
    private final Clock clock;
    private final dev.network.content.post.ContentService content;

    public AttachmentController(
            JdbcTemplate db, Clock clock, dev.network.content.post.ContentService content) {
        this.db = db;
        this.clock = clock;
        this.content = content;
    }

    public List<String> references(String id) {
        return db.queryForList(
                "SELECT media_id FROM media_references WHERE resource_id=? ORDER BY position",
                String.class,
                id);
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Attachment operation conflict");
    }

    @PostMapping("/{action}")
    @Transactional
    public State operation(@PathVariable String action, @Valid @RequestBody Operation in) {
        UUID.fromString(in.operationId());
        UUID.fromString(in.resourceId());
        UUID.fromString(in.actorId());
        in.mediaIds().forEach(UUID::fromString);
        if (new HashSet<>(in.mediaIds()).size() != in.mediaIds().size())
            throw new IllegalArgumentException("Duplicate media");
        if (!Set.of("prepare", "commit", "resolve").contains(action))
            throw new IllegalArgumentException("Unknown action");
        var owners =
                db.queryForList(
                        "SELECT author_id FROM posts WHERE id=? AND deleted_at IS NULL FOR UPDATE",
                        String.class,
                        in.resourceId());
        if (!action.equals("resolve") && (owners.isEmpty() || !owners.getFirst().equals(in.actorId())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        var rows =
                db.queryForList(
                        "SELECT actor_id,resource_id,media_ids,state FROM media_operations WHERE id=?",
                        in.operationId());
        String desired = String.join(",", in.mediaIds());
        if (rows.isEmpty()) {
            if (!action.equals("prepare")) return new State("ABORTED", references(in.resourceId()));
            // The resource row lock fences concurrent edits and abandoned operations.
            db.update(
                    "UPDATE media_operations SET state='ABORTED' WHERE resource_id=? AND state='PREPARED' AND"
                            + " created_at<?",
                    in.resourceId(),
                    java.sql.Timestamp.from(clock.instant().minusSeconds(600)));
            if (db.queryForObject(
                    "SELECT COUNT(*) FROM media_operations WHERE resource_id=? AND state='PREPARED'",
                    Long.class,
                    in.resourceId())
                    > 0) throw conflict();
            db.update(
                    "INSERT INTO media_operations(id,resource_id,actor_id,media_ids,state,created_at)"
                            + " VALUES(?,?,?,?,'PREPARED',?)",
                    in.operationId(),
                    in.resourceId(),
                    in.actorId(),
                    desired,
                    java.sql.Timestamp.from(clock.instant()));
            return new State("PREPARED", references(in.resourceId()));
        }
        var row = rows.getFirst();
        if (!in.actorId().equals(row.get("ACTOR_ID"))
                || !in.resourceId().equals(row.get("RESOURCE_ID"))
                || !desired.equals(Objects.toString(row.get("MEDIA_IDS"), ""))) throw conflict();
        String state = (String) row.get("STATE");
        if (action.equals("resolve") && state.equals("PREPARED")) {
            db.update("UPDATE media_operations SET state='ABORTED' WHERE id=?", in.operationId());
            state = "ABORTED";
        } else if (action.equals("commit") && state.equals("PREPARED")) {
            db.update("DELETE FROM media_references WHERE resource_id=?", in.resourceId());
            for (int i = 0; i < in.mediaIds().size(); i++)
                db.update(
                        "INSERT INTO media_references(resource_id,media_id,position) VALUES(?,?,?)",
                        in.resourceId(),
                        in.mediaIds().get(i),
                        i);
            db.update("UPDATE media_operations SET state='COMMITTED' WHERE id=?", in.operationId());
            state = "COMMITTED";
        }
        if (!action.equals("resolve") && state.equals("ABORTED")) throw conflict();
        return new State(state, owners.isEmpty() ? List.of() : references(in.resourceId()));
    }

    @PostMapping("/access")
    @Transactional(readOnly = true)
    public boolean access(@Valid @RequestBody Access in) {
        content.detail(in.actorId(), in.resourceId());
        return references(in.resourceId()).contains(in.mediaId());
    }

    public record Operation(
            @NotBlank String operationId,
            @NotBlank String resourceId,
            @NotBlank String actorId,
            @NotNull @Size(max = 4) List<String> mediaIds) {
    }

    public record State(String state, List<String> mediaIds) {
    }

    public record Access(
            @NotBlank String resourceId, @NotBlank String actorId, @NotBlank String mediaId) {
    }
}
