package dev.network.hiring.company;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "companies")
public class Company {
    @Id
    public String id;

    @Column(nullable = false)
    public String ownerId;

    @Column(nullable = false, length = 120)
    public String displayName;

    @Column(nullable = false, length = 80)
    public String slug;

    @Lob
    @Column(nullable = false)
    public String description;

    @Column(nullable = false, length = 100)
    public String industry;

    @Column(nullable = false, length = 150)
    public String location;

    @Column(length = 500)
    public String website;

    public Instant createdAt, updatedAt;
    @Version
    public long version;
}
