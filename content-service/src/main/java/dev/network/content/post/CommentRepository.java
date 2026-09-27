package dev.network.content.post;

import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, String> {
  List<Comment> findByPostIdOrderByCreatedAtAscIdAsc(String id, Pageable page);

  long countByPostId(String id);
}
