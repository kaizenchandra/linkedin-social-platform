package dev.network.member.policy;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "member_blocks")
public class MemberBlock {
  @Id public String id;
  public String blockerId;
  public String blockedId;
  public Instant createdAt;
}
