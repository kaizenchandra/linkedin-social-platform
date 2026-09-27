package dev.network.member.profile;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "members")
public class Member {
  @Id public String id;

  @Column(nullable = false, length = 100)
  public String displayName;

  @Column(length = 200)
  public String headline;

  @Lob public String summary;

  @Column(length = 100)
  public String location;

  @Column(nullable = false)
  public Instant createdAt;

  @Version public long version;

  protected Member() {}

  public Member(String id, String name, Instant now) {
    this.id = id;
    this.displayName = name;
    this.createdAt = now;
  }
}
