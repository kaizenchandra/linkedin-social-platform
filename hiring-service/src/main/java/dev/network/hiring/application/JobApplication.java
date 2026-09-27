package dev.network.hiring.application;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "job_applications")
public class JobApplication {
  @Id public String id;
  @Column(nullable = false)
  public String companyId, jobId, applicantId, submissionKey;
  @Lob
  @Column(nullable = false)
  public String requestJson, profileSnapshot, jobSnapshot;
  @Lob public String coverNote;

  @Column(nullable = false, length = 120)
  public String companyName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public ApplicationState state = ApplicationState.SUBMITTED;

  public Instant createdAt, updatedAt;
  @Version public long version;
}
