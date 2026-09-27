package dev.network.content.post;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "post_likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "member_id"}))
public class PostLike {
    @Id
    public String id;

    @Column(nullable = false)
    public String postId;

    @Column(nullable = false)
    public String memberId;

    @Column(nullable = false)
    public Instant createdAt;

    protected PostLike() {
    }
}
