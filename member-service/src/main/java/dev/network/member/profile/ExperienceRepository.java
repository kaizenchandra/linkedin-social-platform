package dev.network.member.profile;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperienceRepository extends JpaRepository<Experience, String> {
    List<Experience> findByMemberIdOrderByPosition(String id);

    void deleteByMemberId(String id);
}
