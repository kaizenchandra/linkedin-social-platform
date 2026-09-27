package dev.network.messaging.events;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "outbox")
public class Outbox {
    @Id
    public String id;

    @Column(nullable = false)
    public String aggregateId;

    @Lob
    @Column(nullable = false)
    public String envelope;

    public String traceParent;

    @Column(nullable = false)
    public Instant createdAt;

    public Instant deliveredAt;
    public int attempts;
    public Instant nextAttemptAt;

    protected Outbox() {
    }
}
