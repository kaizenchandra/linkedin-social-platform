package dev.network.notification.inbox;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(columnNames = {"eventId", "recipientId"}))
public class Notification {
  @Id public String id;

  @Column(nullable = false)
  public String eventId;

  @Column(nullable = false)
  public String recipientId;

  @Column(nullable = false)
  public String actorId;

  @Column(nullable = false)
  public String resourceId;

  @Column(nullable = false)
  public String eventType;

  @Column(nullable = false)
  public Instant occurredAt;

  public Instant readAt;

  protected Notification() {}

  public Notification(
      String id,
      String event,
      String recipient,
      String actor,
      String resource,
      String type,
      Instant time) {
    this.id = id;
    eventId = event;
    recipientId = recipient;
    actorId = actor;
    resourceId = resource;
    eventType = type;
    occurredAt = time;
  }
}
