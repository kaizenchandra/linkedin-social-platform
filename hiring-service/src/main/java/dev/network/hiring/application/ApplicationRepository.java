package dev.network.hiring.application;

import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.*;

public interface ApplicationRepository extends JpaRepository<JobApplication, String> {
    Optional<JobApplication> findByApplicantIdAndSubmissionKey(
            String applicantId, String submissionKey);

    boolean existsByApplicantIdAndJobId(String applicantId, String jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from JobApplication a where a.id=:id")
    Optional<JobApplication> lock(String id);
}
