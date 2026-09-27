package dev.network.content.post;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface PostRepository extends JpaRepository<Post, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Post p where p.id=:id")
  Optional<Post> lock(String id);

  @Query(
      "select p from Post p where p.authorId in :authors and (p.createdAt<:time or"
          + " (p.createdAt=:time and p.id<:id)) order by p.createdAt desc,p.id desc")
  List<Post> feed(List<String> authors, Instant time, String id, Pageable page);
}
