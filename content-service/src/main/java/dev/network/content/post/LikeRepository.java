package dev.network.content.post;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<PostLike, String> {
    Optional<PostLike> findByPostIdAndMemberId(String post, String member);

    long countByPostId(String id);
}
