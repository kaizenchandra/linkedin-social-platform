package dev.network.member.profile;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface MemberRepository extends JpaRepository<Member, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Member m where m.id=:id")
  Optional<Member> lock(String id);

  @Query(
      "select m from Member m where lower(m.displayName) like :term escape '!' or lower(m.headline)"
          + " like :term escape '!' order by m.displayName,m.id")
  List<Member> search(String term, Pageable pageable);
}
