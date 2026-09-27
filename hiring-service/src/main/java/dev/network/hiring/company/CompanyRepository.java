package dev.network.hiring.company;

import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.*;

public interface CompanyRepository extends JpaRepository<Company, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Company c where c.id=:id")
    Optional<Company> lock(String id);
}
