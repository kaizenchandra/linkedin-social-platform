package dev.network.content.post;

import java.util.*;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, String> {
    @org.springframework.data.jpa.repository.Query(
            "select c from Comment c where c.postId=:id and c.hidden=false and c.deletedAt is null and"
                    + " c.authorId in :authors order by c.createdAt,c.id")
    List<Comment> visible(String id, List<String> authors, Pageable page);

    long countByPostIdAndHiddenFalseAndDeletedAtIsNull(String id);
}
