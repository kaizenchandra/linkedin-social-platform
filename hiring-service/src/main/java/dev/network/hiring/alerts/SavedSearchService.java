package dev.network.hiring.alerts;

import dev.network.hiring.company.CompanyService;
import dev.network.hiring.job.*;
import dev.network.hiring.shared.MemberLocks;
import jakarta.validation.constraints.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedSearchService {
    private final JdbcTemplate db;
    private final MemberLocks locks;
    private final EligibilityEpoch epoch;
    private final CompanyService companies;

    public SavedSearchService(
            JdbcTemplate db, MemberLocks locks, EligibilityEpoch epoch, CompanyService companies) {
        this.db = db;
        this.locks = locks;
        this.epoch = epoch;
        this.companies = companies;
    }

    private void validateCompanies(SearchCriteria criteria) {
        for (String id : criteria.companyIds())
            if (db.queryForObject("SELECT COUNT(*) FROM companies WHERE id=?", Long.class, id) != 1)
                throw CompanyService.missing();
    }

    private void selections(String id, SearchCriteria criteria) {
        db.update("DELETE FROM saved_search_companies WHERE search_id=?", id);
        for (String company : criteria.companyIds())
            db.update(
                    "INSERT INTO saved_search_companies(search_id,company_id) VALUES(?,?)", id, company);
    }

    @Transactional
    public View create(String actor, Input in) {
        var criteria = in.criteria();
        validateCompanies(criteria);
        locks.lock(actor);
        if (db.queryForObject(
                "SELECT COUNT(*) FROM saved_searches WHERE member_id=? AND deleted=0",
                Long.class,
                actor)
                >= 10) throw CompanyService.conflict("Saved search limit10");
        String id = UUID.randomUUID().toString();
        long order = epoch.next();
        var now = Timestamp.from(companies.now());
        db.update(
                "INSERT INTO"
                        + " saved_searches(id,member_id,name,keywords,location,work_arrangement,employment_type,enabled,criteria_epoch,activation_epoch,created_at,updated_at)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                actor,
                in.name().strip(),
                criteria.keywords(),
                criteria.location(),
                criteria.workArrangement() == null ? null : criteria.workArrangement().name(),
                criteria.employmentType() == null ? null : criteria.employmentType().name(),
                in.alertsEnabled() ? 1 : 0,
                order,
                order,
                now,
                now);
        selections(id, criteria);
        return get(actor, id);
    }

    @Transactional
    public View update(String actor, String id, Input in) {
        var criteria = in.criteria();
        validateCompanies(criteria);
        locks.lock(actor);
        var old = get(actor, id);
        if (in.expectedVersion() == null || in.expectedVersion() != old.version())
            throw CompanyService.conflict("Stale saved search version");
        boolean changed = !criteria.equals(old.criteria()),
                activated = in.alertsEnabled() && !old.alertsEnabled();
        long order = changed || activated ? epoch.next() : old.criteriaEpoch();
        db.update(
                "UPDATE saved_searches SET"
                        + " name=?,keywords=?,location=?,work_arrangement=?,employment_type=?,enabled=?,criteria_version=?,version=version+1,criteria_epoch=?,activation_epoch=?,updated_at=?"
                        + " WHERE id=?",
                in.name().strip(),
                criteria.keywords(),
                criteria.location(),
                criteria.workArrangement() == null ? null : criteria.workArrangement().name(),
                criteria.employmentType() == null ? null : criteria.employmentType().name(),
                in.alertsEnabled() ? 1 : 0,
                old.criteriaVersion() + (changed ? 1 : 0),
                changed ? order : old.criteriaEpoch(),
                activated ? order : old.activationEpoch(),
                Timestamp.from(companies.now()),
                id);
        if (changed) selections(id, criteria);
        return get(actor, id);
    }

    @Transactional
    public void delete(String actor, String id) {
        UUID.fromString(id);
        locks.lock(actor);
        db.update(
                "UPDATE saved_searches SET deleted=1,enabled=0,version=version+1,updated_at=? WHERE id=?"
                        + " AND member_id=? AND deleted=0",
                Timestamp.from(companies.now()),
                id,
                actor);
    }

    @Transactional(readOnly = true)
    public View get(String actor, String id) {
        UUID.fromString(id);
        return list(actor).stream()
                .filter(v -> v.id().equals(id))
                .findFirst()
                .orElseThrow(CompanyService::missing);
    }

    @Transactional(readOnly = true)
    public List<View> list(String actor) {
        var selected = new HashMap<String, List<String>>();
        db.query(
                "SELECT c.search_id,c.company_id FROM saved_search_companies c JOIN saved_searches s ON"
                        + " s.id=c.search_id WHERE s.member_id=? AND s.deleted=0 ORDER BY"
                        + " c.search_id,c.company_id",
                (org.springframework.jdbc.core.RowCallbackHandler)
                        r ->
                                selected
                                        .computeIfAbsent(r.getString(1), k -> new ArrayList<>())
                                        .add(r.getString(2)),
                actor);
        return db.query(
                "SELECT * FROM saved_searches WHERE member_id=? AND deleted=0 ORDER BY id FETCH FIRST 10"
                        + " ROWS ONLY",
                (r, n) ->
                        new View(
                                r.getString("id"),
                                r.getString("name"),
                                new SearchCriteria(
                                        r.getString("keywords"),
                                        selected.getOrDefault(r.getString("id"), List.of()),
                                        r.getString("location"),
                                        r.getString("work_arrangement") == null
                                                ? null
                                                : Job.Work.valueOf(r.getString("work_arrangement")),
                                        r.getString("employment_type") == null
                                                ? null
                                                : Job.Employment.valueOf(r.getString("employment_type"))),
                                r.getInt("enabled") == 1,
                                r.getLong("criteria_version"),
                                r.getLong("version"),
                                r.getLong("criteria_epoch"),
                                r.getLong("activation_epoch"),
                                r.getTimestamp("created_at").toInstant(),
                                r.getTimestamp("updated_at").toInstant()),
                actor);
    }

    @Transactional(readOnly = true)
    public boolean consent(String actor) {
        UUID.fromString(actor);
        return db.queryForObject(
                "SELECT COUNT(*) FROM saved_searches WHERE member_id=? AND deleted=0 AND enabled=1",
                Long.class,
                actor)
                > 0;
    }

    public record Input(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String keywords,
            @Size(max = 20) List<@NotBlank String> companyIds,
            @Size(max = 150) String location,
            Job.Work workArrangement,
            Job.Employment employmentType,
            boolean alertsEnabled,
            Long expectedVersion) {
        public SearchCriteria criteria() {
            return new SearchCriteria(keywords, companyIds, location, workArrangement, employmentType);
        }
    }

    public record View(
            String id,
            String name,
            SearchCriteria criteria,
            boolean alertsEnabled,
            long criteriaVersion,
            long version,
            long criteriaEpoch,
            long activationEpoch,
            Instant createdAt,
            Instant updatedAt) {
    }
}
