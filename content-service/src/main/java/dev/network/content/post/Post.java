package dev.network.content.post;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {
  @Id public String id;

  @Column(nullable = false)
  public String authorId;

  @Lob
  @Column(nullable = false)
  public String body;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;

  @Version public long version;

  protected Post() {}
}
