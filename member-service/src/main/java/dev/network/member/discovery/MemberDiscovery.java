package dev.network.member.discovery;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberDiscovery {
  private final JdbcTemplate db;
  private final MeterRegistry metrics;

  public MemberDiscovery(JdbcTemplate db, MeterRegistry metrics) {
    this.db = db;
    this.metrics = metrics;
  }

  public record Suggestion(String memberId, String displayName, String headline, String reason) {}

  @Transactional(readOnly = true)
  public List<Suggestion> suggest(String actor, int size) {
    if (size < 1 || size > 20) throw new IllegalArgumentException("size must be1..20");
    return metrics
        .timer("network.discovery.query", "query", "member_suggestions")
        .record(
            () ->
                db.query(
                    """
                    WITH friends AS (
                      SELECT high_id AS id FROM connections WHERE low_id=? AND state='ACCEPTED'
                      UNION ALL SELECT low_id FROM connections WHERE high_id=? AND state='ACCEPTED'
                    ), edges AS (
                      SELECT c.low_id,c.high_id,f.id AS friend_id,
                        CASE WHEN c.low_id=f.id THEN c.high_id ELSE c.low_id END AS candidate
                      FROM friends f JOIN connections c ON (c.low_id=f.id OR c.high_id=f.id)
                      WHERE c.state='ACCEPTED'
                      ORDER BY c.low_id,c.high_id,f.id FETCH FIRST 5000 ROWS ONLY
                    ), candidates AS (SELECT candidate,COUNT(DISTINCT friend_id) AS score FROM edges GROUP BY candidate)
                    SELECT m.id,m.display_name,m.headline FROM candidates d JOIN members m ON m.id=d.candidate
                    WHERE m.id<>?
                      AND NOT EXISTS(SELECT 1 FROM connections c WHERE c.state IN ('ACCEPTED','PENDING') AND ((c.low_id=? AND c.high_id=m.id) OR (c.high_id=? AND c.low_id=m.id)))
                      AND NOT EXISTS(SELECT 1 FROM member_follows f WHERE f.follower_id=? AND f.followed_id=m.id)
                      AND NOT EXISTS(SELECT 1 FROM member_blocks b WHERE (b.blocker_id=? AND b.blocked_id=m.id) OR (b.blocked_id=? AND b.blocker_id=m.id))
                    ORDER BY d.score DESC,m.id FETCH FIRST ? ROWS ONLY
                    """,
                    (r, n) ->
                        new Suggestion(
                            r.getString(1),
                            r.getString(2),
                            r.getString(3),
                            "Connections in common"),
                    actor,
                    actor,
                    actor,
                    actor,
                    actor,
                    actor,
                    actor,
                    actor,
                    size));
  }
}
