package dev.network.member.connection;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "connections",
    uniqueConstraints = @UniqueConstraint(columnNames = {"low_id", "high_id"}))
public class Connection {
  @Id public String id;

  @Column(nullable = false)
  public String lowId;

  @Column(nullable = false)
  public String highId;

  @Column(nullable = false)
  public String requesterId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public State state;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;

  @Version public long version;

  protected Connection() {}

  public enum State {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    REMOVED
  }

  public boolean participant(String id) {
    return lowId.equals(id) || highId.equals(id);
  }

  public String other(String id) {
    return lowId.equals(id) ? highId : lowId;
  }

  public boolean transition(String actor, String action) {
    if (!participant(actor)) throw new IllegalArgumentException("not participant");
    State desired =
        switch (action) {
          case "accept" -> State.ACCEPTED;
          case "reject" -> State.REJECTED;
          case "cancel" -> State.CANCELLED;
          case "remove" -> State.REMOVED;
          default -> throw new IllegalArgumentException("unknown action");
        };
    if ((action.equals("accept") || action.equals("reject")) && requesterId.equals(actor))
      throw new IllegalStateException("Only recipient may respond");
    if (action.equals("cancel") && !requesterId.equals(actor))
      throw new IllegalStateException("Only sender may cancel");
    if (state == desired) return false;
    if (action.equals("remove") ? state != State.ACCEPTED : state != State.PENDING)
      throw new IllegalStateException("Invalid relationship transition");
    state = desired;
    return true;
  }
}
