package dev.network.hiring.job;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "jobs")
public class Job {
    @Id
    public String id;
    @Column(nullable = false)
    public String companyId;
    @Column(nullable = false, length = 160)
    public String title;
    @Lob
    @Column(nullable = false)
    public String description;
    @Column(nullable = false, length = 150)
    public String location;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Work workArrangement;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Employment employmentType;
    @Column(precision = 19, scale = 2)
    public BigDecimal salaryMinimum, salaryMaximum;
    @Column(length = 3)
    public String salaryCurrency;
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    public Period payPeriod;
    public Instant deadline, createdAt, updatedAt, publishedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public State state = State.DRAFT;
    @Convert(converter = org.hibernate.type.NumericBooleanConverter.class)
    @Column(nullable = false)
    public boolean hidden;
    @Version
    public long version;

    public enum State {
        DRAFT,
        PUBLISHED,
        CLOSED
    }

    public enum Work {
        ONSITE,
        HYBRID,
        REMOTE
    }

    public enum Employment {
        FULL_TIME,
        PART_TIME,
        CONTRACT,
        INTERNSHIP
    }

    public enum Period {
        HOUR,
        MONTH,
        YEAR
    }
}
