package dev.network.member.profile;

import jakarta.persistence.LockModeType;

import java.util.*;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface MemberRepository extends JpaRepository<Member, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id=:id")
    Optional<Member> lock(String id);

    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("select m from Member m where m.id=:id")
    Optional<Member> lockForProfileWrite(String id);

    @Query(
            "select m from Member m where (lower(m.displayName) like :term escape '!' or"
                    + " lower(m.headline) like :term escape '!') and not exists (select b.id from MemberBlock"
                    + " b where (b.blockerId=:actor and b.blockedId=m.id) or (b.blockedId=:actor and"
                    + " b.blockerId=m.id)) order by m.displayName,m.id")
    List<Member> search(String actor, String term, Pageable pageable);
}
