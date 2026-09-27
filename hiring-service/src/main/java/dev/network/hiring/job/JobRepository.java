package dev.network.hiring.job;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface JobRepository extends JpaRepository<Job, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from Job j where j.id=:id")
  Optional<Job> lock(String id);
}
