package dev.network.content.post;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {
    @Id
    public String id;

    @Column(nullable = false)
    public String authorId;

    @Lob
    @Column(nullable = false)
    public String body;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant updatedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Visibility visibility = Visibility.MEMBERS;
    @Convert(converter = org.hibernate.type.NumericBooleanConverter.class)
    public boolean hidden;
    public Instant deletedAt;
    @Version
    public long version;
    protected Post() {
    }

    public enum Visibility {
        MEMBERS,
        CONNECTIONS
    }
}
