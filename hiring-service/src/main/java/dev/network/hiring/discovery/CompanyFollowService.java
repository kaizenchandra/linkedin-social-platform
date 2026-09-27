package dev.network.hiring.discovery;

import dev.network.hiring.company.CompanyService;
import dev.network.hiring.shared.*;
import dev.network.web.Pages;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyFollowService {
    private final JdbcTemplate db;
    private final MemberLocks locks;
    private final CompanyService companies;

    public CompanyFollowService(JdbcTemplate db, MemberLocks locks, CompanyService companies) {
        this.db = db;
        this.locks = locks;
        this.companies = companies;
    }

    @Transactional
    public void follow(String actor, String id) {
        locks.lock(actor);
        companies.lock(id);
        if (status(actor, id)) return;
        if (db.queryForObject(
                "SELECT COUNT(*) FROM company_follows WHERE member_id=?", Long.class, actor)
                >= 500) throw CompanyService.conflict("Company follow limit500");
        db.update(
                "INSERT INTO company_follows(member_id,company_id,created_at) VALUES(?,?,?)",
                actor,
                id,
                Timestamp.from(companies.now()));
    }

    @Transactional
    public void unfollow(String actor, String id) {
        locks.lock(actor);
        UUID.fromString(id);
        db.update("DELETE FROM company_follows WHERE member_id=? AND company_id=?", actor, id);
    }

    @Transactional(readOnly = true)
    public boolean status(String actor, String id) {
        UUID.fromString(id);
        return db.queryForObject(
                "SELECT COUNT(*) FROM company_follows WHERE member_id=? AND company_id=?",
                Long.class,
                actor,
                id)
                > 0;
    }

    @Transactional(readOnly = true)
    public HiringPages.Slice<Follow> list(String actor, String cursor, int size) {
        Pages.size(size);
        var args = new ArrayList<Object>();
        args.add(actor);
        String after =
                HiringPages.after(cursor, args, "f.created_at").replace("id<?", "f.company_id<?");
        args.add(size + 1);
        var rows =
                db.query(
                        "SELECT f.company_id,c.display_name,c.slug,f.created_at FROM company_follows f JOIN"
                                + " companies c ON c.id=f.company_id WHERE f.member_id=?"
                                + after
                                + " ORDER BY f.created_at DESC,f.company_id DESC FETCH FIRST ? ROWS ONLY",
                        (r, n) ->
                                new Follow(
                                        r.getString(1), r.getString(2), r.getString(3), r.getTimestamp(4).toInstant()),
                        args.toArray());
        return HiringPages.slice(rows, size, v -> HiringPages.cursor(v.followedAt(), v.companyId()));
    }

    public record Follow(String companyId, String displayName, String slug, Instant followedAt) {
    }
}
