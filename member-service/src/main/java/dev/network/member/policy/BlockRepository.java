package dev.network.member.policy;

import java.util.*;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface BlockRepository extends JpaRepository<MemberBlock, String> {
    Optional<MemberBlock> findByBlockerIdAndBlockedId(String blockerId, String blockedId);

    @Query(
            "select count(b) from MemberBlock b where (b.blockerId=:a and b.blockedId=:z) or"
                    + " (b.blockerId=:z and b.blockedId=:a)")
    long between(String a, String z);

    @Query(
            "select b from MemberBlock b where (b.blockerId=:actor and b.blockedId in :targets) or"
                    + " (b.blockedId=:actor and b.blockerId in :targets)")
    List<MemberBlock> relevant(String actor, List<String> targets);

    List<MemberBlock> findByBlockerIdOrderByCreatedAtDescIdDesc(String actor, Pageable page);
}
