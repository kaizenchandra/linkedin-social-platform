package dev.network.member.follow;

import dev.network.member.policy.PolicyService;
import dev.network.member.profile.MemberRepository;
import dev.network.web.Pages;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FollowService {
  public static final int LIMIT = 500;
  private final MemberRepository members;
  private final PolicyService policy;
  private final JdbcTemplate db;
  private final Clock clock;

  public FollowService(
      MemberRepository members, PolicyService policy, JdbcTemplate db, Clock clock) {
    this.members = members;
    this.policy = policy;
    this.db = db;
    this.clock = clock;
  }

  public record Follow(String memberId, Instant followedAt) {}

  private void pair(String actor, String target) {
    UUID.fromString(target);
    for (String id : java.util.stream.Stream.of(actor, target).distinct().sorted().toList())
      members
          .lock(id)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
  }

  @Transactional
  public void follow(String actor, String target) {
    if (actor.equals(target)) throw new IllegalArgumentException("Cannot follow yourself");
    pair(actor, target);
    policy.requireVisible(actor, target);
    if (status(actor, target)) return;
    if (db.queryForObject(
            "SELECT COUNT(*) FROM member_follows WHERE follower_id=?", Long.class, actor)
        >= LIMIT) throw new ResponseStatusException(HttpStatus.CONFLICT, "Member follow limit500");
    db.update(
        "INSERT INTO member_follows(follower_id,followed_id,created_at) VALUES(?,?,?)",
        actor,
        target,
        Timestamp.from(clock.instant()));
  }

  @Transactional
  public void unfollow(String actor, String target) {
    pair(actor, target);
    db.update("DELETE FROM member_follows WHERE follower_id=? AND followed_id=?", actor, target);
  }

  @Transactional(readOnly = true)
  public boolean status(String actor, String target) {
    UUID.fromString(target);
    return db.queryForObject(
            "SELECT COUNT(*) FROM member_follows WHERE follower_id=? AND followed_id=?",
            Long.class,
            actor,
            target)
        > 0;
  }

  @Transactional(readOnly = true)
  public List<Follow> list(String actor, int page, int size) {
    return db.query(
        "SELECT followed_id,created_at FROM member_follows WHERE follower_id=? ORDER BY created_at"
            + " DESC,followed_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        (r, n) -> new Follow(r.getString(1), r.getTimestamp(2).toInstant()),
        actor,
        Pages.page(page) * Pages.size(size),
        size);
  }

  @Transactional(readOnly = true)
  public List<String> ids(String actor) {
    UUID.fromString(actor);
    var ids =
        db.queryForList(
            "SELECT followed_id FROM member_follows WHERE follower_id=? ORDER BY followed_id FETCH"
                + " FIRST 501 ROWS ONLY",
            String.class,
            actor);
    if (ids.size() > LIMIT)
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Follow bound exceeded");
    return ids;
  }
}
