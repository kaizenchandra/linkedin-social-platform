package dev.network.hiring.company;

import dev.network.hiring.shared.HiringPages;
import dev.network.web.*;
import jakarta.validation.constraints.*;

import java.net.URI;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyService {
    private final CompanyRepository repo;
    private final JdbcTemplate db;
    private final Clock clock;
    private final ServiceHttp http;
    private final String memberUrl;
    private final dev.network.hiring.events.EventWriter events;

    public CompanyService(
            CompanyRepository repo,
            JdbcTemplate db,
            Clock clock,
            ServiceHttp http,
            dev.network.hiring.events.EventWriter events,
            @Value("${MEMBER_URL:http://localhost:8081}") String memberUrl) {
        this.events = events;
        this.repo = repo;
        this.db = db;
        this.clock = clock;
        this.http = http;
        this.memberUrl = memberUrl;
    }

    public static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Hiring resource not found");
    }

    public static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public Instant now() {
        return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public Company lock(String id) {
        UUID.fromString(id);
        return repo.lock(id).orElseThrow(CompanyService::missing);
    }

    public boolean member(String company, String actor) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM company_members WHERE company_id=? AND member_id=?",
                Long.class,
                company,
                actor)
                > 0;
    }

    public void requireMember(Company c, String actor) {
        if (!member(c.id, actor)) throw missing();
    }

    public void requireOwner(Company c, String actor) {
        if (!c.ownerId.equals(actor)) throw missing();
    }

    public void audit(String company, String actor, String action, String target, String reason) {
        db.update(
                "INSERT INTO hiring_audit(id,company_id,actor_id,action,target_id,reason,occurred_at)"
                        + " VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),
                company,
                actor,
                action,
                target,
                reason,
                Timestamp.from(now()));
    }

    private void exists(String member) {
        UUID.fromString(member);
        if (!Boolean.TRUE.equals(
                http.post(
                        memberUrl + "/internal/v1/hiring/member-exists",
                        Map.of("memberId", member),
                        Boolean.class))) throw missing();
    }

    private void apply(Company c, Input in) {
        String slug = in.slug().strip().toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 80)
            throw new IllegalArgumentException("Invalid company slug");
        if (in.website() != null && !in.website().isBlank()) {
            URI u = URI.create(in.website());
            if (!Set.of("http", "https")
                    .contains(Objects.toString(u.getScheme(), "").toLowerCase(Locale.ROOT))
                    || u.getHost() == null
                    || u.getUserInfo() != null
                    || u.getPort() > 65535)
                throw new IllegalArgumentException("Website must be an HTTP(S) URL");
        }
        c.displayName = in.displayName().strip();
        c.slug = slug;
        c.description = in.description();
        c.industry = in.industry().strip();
        c.location = in.location().strip();
        c.website = in.website() == null || in.website().isBlank() ? null : in.website();
        c.updatedAt = now();
    }

    @Transactional
    public View create(String actor, Input in) {
        exists(actor);
        Company c = new Company();
        c.id = UUID.randomUUID().toString();
        c.ownerId = actor;
        c.createdAt = now();
        apply(c, in);
        repo.saveAndFlush(c);
        db.update(
                "INSERT INTO company_members(company_id,member_id,joined_at) VALUES(?,?,?)",
                c.id,
                actor,
                Timestamp.from(now()));
        audit(c.id, actor, "COMPANY_CREATED", c.id, "Self-created, unverified company");
        return view(c);
    }

    @Transactional
    public View edit(String actor, String id, Input in) {
        var c = lock(id);
        requireOwner(c, actor);
        apply(c, in);
        repo.flush();
        audit(id, actor, "COMPANY_EDITED", id, "Company details edited");
        return view(c);
    }

    @Transactional(readOnly = true)
    public View get(String id) {
        return view(repo.findById(id).orElseThrow(CompanyService::missing));
    }

    private View view(Company c) {
        return new View(
                c.id,
                c.displayName,
                c.slug,
                c.description,
                c.industry,
                c.location,
                c.website,
                "UNVERIFIED",
                db
                        .queryForList(
                                "SELECT media_id FROM media_references WHERE resource_id=?", String.class, c.id)
                        .stream()
                        .findFirst()
                        .orElse(null),
                c.createdAt,
                c.version);
    }

    @Transactional(readOnly = true)
    public HiringPages.Slice<View> list(String actor, boolean mine, String cursor, int size) {
        Pages.size(size);
        var args = new ArrayList<Object>();
        String where = "";
        if (mine) {
            where =
                    " AND EXISTS(SELECT 1 FROM company_members m WHERE m.company_id=c.id AND m.member_id=?)";
            args.add(actor);
        }
        where += HiringPages.after(cursor, args, "created_at");
        args.add(size + 1);
        var rows =
                db.query(
                        "SELECT c.*, (SELECT media_id FROM media_references r WHERE r.resource_id=c.id) logo"
                                + " FROM companies c WHERE 1=1"
                                + where
                                + " ORDER BY created_at DESC,id DESC FETCH FIRST ? ROWS ONLY",
                        (r, n) ->
                                new View(
                                        r.getString("id"),
                                        r.getString("display_name"),
                                        r.getString("slug"),
                                        r.getString("description"),
                                        r.getString("industry"),
                                        r.getString("location"),
                                        r.getString("website"),
                                        "UNVERIFIED",
                                        r.getString("logo"),
                                        r.getTimestamp("created_at").toInstant(),
                                        r.getLong("version")),
                        args.toArray());
        return HiringPages.slice(rows, size, v -> HiringPages.cursor(v.createdAt(), v.id()));
    }

    @Transactional(readOnly = true)
    public List<Membership> members(String actor, String id) {
        var c = repo.findById(id).orElseThrow(CompanyService::missing);
        requireMember(c, actor);
        return db.query(
                "SELECT member_id,joined_at FROM company_members WHERE company_id=? ORDER BY"
                        + " joined_at,member_id",
                (r, n) ->
                        new Membership(
                                r.getString(1),
                                c.ownerId.equals(r.getString(1)) ? "OWNER" : "RECRUITER",
                                r.getTimestamp(2).toInstant()),
                id);
    }

    @Transactional
    public void remove(String actor, String id, String target) {
        var c = lock(id);
        requireOwner(c, actor);
        if (c.ownerId.equals(target)) throw conflict("Transfer ownership before removing owner");
        if (db.update("DELETE FROM company_members WHERE company_id=? AND member_id=?", id, target) > 0)
            audit(id, actor, "RECRUITER_REMOVED", target, "Company access revoked");
    }

    @Transactional
    public View transfer(String actor, String id, String target) {
        var c = lock(id);
        requireOwner(c, actor);
        if (c.ownerId.equals(target)) return view(c);
        if (!member(id, target)) throw conflict("New owner must be an accepted company member");
        c.ownerId = target;
        c.updatedAt = now();
        repo.flush();
        audit(id, actor, "OWNERSHIP_TRANSFERRED", target, "Previous owner becomes recruiter");
        return view(c);
    }

    private Invitation invitation(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        var until = r.getTimestamp("expires_at").toInstant();
        String state = r.getString("state");
        return new Invitation(
                r.getString("id"),
                r.getString("company_id"),
                r.getString("member_id"),
                state.equals("PENDING") && !now().isBefore(until) ? "EXPIRED" : state,
                r.getTimestamp("created_at").toInstant(),
                until);
    }

    @Transactional
    public Invitation invite(String actor, String company, String target) {
        var c = lock(company);
        requireOwner(c, actor);
        exists(target);
        if (member(company, target)) throw conflict("Member already belongs to company");
        db.update(
                "UPDATE company_invitations SET state='EXPIRED' WHERE company_id=? AND state='PENDING' AND"
                        + " expires_at<=?",
                company,
                Timestamp.from(now()));
        var old =
                db.query(
                        "SELECT * FROM company_invitations WHERE company_id=? AND member_id=? AND"
                                + " state='PENDING'",
                        this::invitation,
                        company,
                        target);
        if (!old.isEmpty()) return old.getFirst();
        if (db.queryForObject(
                "SELECT COUNT(*) FROM company_invitations WHERE company_id=? AND state='PENDING'",
                Long.class,
                company)
                >= 100) throw conflict("Company has100 pending invitations");
        var v =
                new Invitation(
                        UUID.randomUUID().toString(),
                        company,
                        target,
                        "PENDING",
                        now(),
                        now().plus(Duration.ofDays(7)));
        db.update(
                "INSERT INTO"
                        + " company_invitations(id,company_id,member_id,invited_by,state,created_at,expires_at)"
                        + " VALUES(?,?,?,?,'PENDING',?,?)",
                v.id(),
                company,
                target,
                actor,
                Timestamp.from(v.createdAt()),
                Timestamp.from(v.expiresAt()));
        audit(company, actor, "RECRUITER_INVITED", target, "Seven-day invitation");
        events.write("hiring.invitation.created", v.id(), 0, actor, target);
        return v;
    }

    @Transactional
    public Invitation invitationAction(String actor, String id, String action) {
        var rows = db.query("SELECT * FROM company_invitations WHERE id=?", this::invitation, id);
        if (rows.isEmpty()) throw missing();
        var c = lock(rows.getFirst().companyId());
        var v =
                db.query("SELECT * FROM company_invitations WHERE id=? FOR UPDATE", this::invitation, id)
                        .getFirst();
        String next =
                switch (action) {
                    case "accept" -> "ACCEPTED";
                    case "reject" -> "REJECTED";
                    case "cancel" -> "CANCELLED";
                    default -> throw new IllegalArgumentException("Invalid invitation action");
                };
        if (action.equals("cancel")) requireOwner(c, actor);
        else if (!v.memberId().equals(actor)) throw missing();
        if (v.state().equals(next)) return v;
        if (!v.state().equals("PENDING")) throw conflict("Invitation is no longer pending");
        if (next.equals("ACCEPTED")) {
            if (db.queryForObject(
                    "SELECT COUNT(*) FROM company_members WHERE company_id=?", Long.class, c.id)
                    >= 100) throw conflict("Company membership limit100");
            if (!member(c.id, actor))
                db.update(
                        "INSERT INTO company_members(company_id,member_id,joined_at) VALUES(?,?,?)",
                        c.id,
                        actor,
                        Timestamp.from(now()));
        }
        db.update("UPDATE company_invitations SET state=? WHERE id=?", next, id);
        audit(c.id, actor, "INVITATION_" + next, id, "Invitation decision");
        return new Invitation(id, c.id, v.memberId(), next, v.createdAt(), v.expiresAt());
    }

    @Transactional(readOnly = true)
    public HiringPages.Slice<Invitation> invitations(
            String actor, String company, String cursor, int size) {
        Pages.size(size);
        var args = new ArrayList<Object>();
        String where;
        if (company != null) {
            var c = repo.findById(company).orElseThrow(CompanyService::missing);
            requireOwner(c, actor);
            where = "company_id=?";
            args.add(company);
        } else {
            where = "member_id=?";
            args.add(actor);
        }
        where += HiringPages.after(cursor, args, "created_at");
        args.add(size + 1);
        var rows =
                db.query(
                        "SELECT * FROM company_invitations WHERE "
                                + where
                                + " ORDER BY created_at DESC,id DESC FETCH FIRST ? ROWS ONLY",
                        this::invitation,
                        args.toArray());
        return HiringPages.slice(rows, size, v -> HiringPages.cursor(v.createdAt(), v.id()));
    }

    @Transactional(readOnly = true)
    public List<Audit> auditHistory(String actor, String company, int page, int size) {
        var c = repo.findById(company).orElseThrow(CompanyService::missing);
        requireOwner(c, actor);
        return db.query(
                "SELECT * FROM hiring_audit WHERE company_id=? ORDER BY occurred_at DESC,id DESC OFFSET ?"
                        + " ROWS FETCH NEXT ? ROWS ONLY",
                (r, n) ->
                        new Audit(
                                r.getString("id"),
                                r.getString("actor_id"),
                                r.getString("action"),
                                r.getString("target_id"),
                                r.getString("reason"),
                                r.getTimestamp("occurred_at").toInstant()),
                company,
                Pages.page(page) * Pages.size(size),
                size);
    }

    public record Input(
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 80) String slug,
            @NotBlank @Size(max = 4000) String description,
            @NotBlank @Size(max = 100) String industry,
            @NotBlank @Size(max = 150) String location,
            @Size(max = 500) String website) {
    }

    public record View(
            String id,
            String displayName,
            String slug,
            String description,
            String industry,
            String location,
            String website,
            String verificationStatus,
            String logoMediaId,
            Instant createdAt,
            long version) {
    }

    public record Membership(String memberId, String role, Instant joinedAt) {
    }

    public record Invitation(
            String id,
            String companyId,
            String memberId,
            String state,
            Instant createdAt,
            Instant expiresAt) {
    }

    public record Audit(
            String id,
            String actorId,
            String action,
            String targetId,
            String reason,
            Instant occurredAt) {
    }
}
