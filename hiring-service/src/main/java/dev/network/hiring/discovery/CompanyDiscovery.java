package dev.network.hiring.discovery;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyDiscovery {
    private final JdbcTemplate db;
    private final MeterRegistry metrics;

    public CompanyDiscovery(JdbcTemplate db, MeterRegistry metrics) {
        this.db = db;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public List<Suggestion> suggest(String actor, String industry, String location, int size) {
        if (size < 1 || size > 20) throw new IllegalArgumentException("size must be1..20");
        if (industry != null && industry.length() > 100 || location != null && location.length() > 150)
            throw new IllegalArgumentException("Filter too long");
        var args = new ArrayList<Object>();
        args.add(actor);
        String where =
                "NOT EXISTS(SELECT 1 FROM company_follows f WHERE f.member_id=? AND f.company_id=c.id)";
        var reasons = new ArrayList<String>();
        if (industry != null && !industry.isBlank()) {
            where += " AND LOWER(c.industry)=?";
            args.add(industry.strip().toLowerCase(Locale.ROOT));
            reasons.add("Industry matches");
        }
        if (location != null && !location.isBlank()) {
            where += " AND LOWER(c.location)=?";
            args.add(location.strip().toLowerCase(Locale.ROOT));
            reasons.add("Location matches");
        }
        args.add(size);
        String reason = reasons.isEmpty() ? "Company directory" : String.join("; ", reasons);
        String sql =
                "SELECT c.id,c.display_name,c.industry,c.location FROM companies c WHERE "
                        + where
                        + " ORDER BY c.id FETCH FIRST ? ROWS ONLY";
        return metrics
                .timer("network.discovery.query", "query", "company_suggestions")
                .record(
                        () ->
                                db.query(
                                        sql,
                                        (r, n) ->
                                                new Suggestion(
                                                        r.getString(1), r.getString(2), r.getString(3), r.getString(4), reason),
                                        args.toArray()));
    }

    public record Suggestion(
            String companyId, String displayName, String industry, String location, String reason) {
    }
}
