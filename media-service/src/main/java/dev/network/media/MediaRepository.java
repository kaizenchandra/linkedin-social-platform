package dev.network.media;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.*;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface MediaRepository extends JpaRepository<MediaObject, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaObject m where m.id=:id")
    Optional<MediaObject> lock(String id);

    @Query("select m from MediaObject m where m.updatedAt<:before order by m.updatedAt,m.id")
    List<MediaObject> stale(Instant before, Pageable page);

    long countByOwnerIdAndCreatedAtAfter(String owner, Instant since);
}
