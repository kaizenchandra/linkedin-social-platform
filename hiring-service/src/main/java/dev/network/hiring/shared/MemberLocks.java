package dev.network.hiring.shared;

import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Serializes private per-member limits and commands within the caller's Oracle transaction. */
@Component
public class MemberLocks {
  private final JdbcTemplate db;

  public MemberLocks(JdbcTemplate db) {
    this.db = db;
  }

  public void lock(String actor) {
    UUID.fromString(actor);
    try {
      db.update(
          "INSERT INTO hiring_member_state(member_id) SELECT ? FROM dual WHERE NOT EXISTS (SELECT 1"
              + " FROM hiring_member_state WHERE member_id=?)",
          actor,
          actor);
    } catch (DuplicateKeyException concurrentInitialization) {
      /* Oracle rolls back only the losing INSERT statement. */
    }
    db.queryForObject(
        "SELECT member_id FROM hiring_member_state WHERE member_id=? FOR UPDATE",
        String.class,
        actor);
  }
}
