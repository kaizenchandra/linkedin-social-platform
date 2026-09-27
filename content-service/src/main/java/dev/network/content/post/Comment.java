package dev.network.content.post;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "comments")
public class Comment {
  @Id public String id;

  @Column(nullable = false)
  public String postId;

  @Column(nullable = false)
  public String authorId;

  @Column(nullable = false, length = 1000)
  public String body;

  @Column(nullable = false)
  public Instant createdAt;

  protected Comment() {}
}
