package com.strataresolve.ticket.repository;

import com.strataresolve.ticket.domain.ReferenceNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for the reference_number_sequence table.
 * Uses pessimistic write locking (SELECT ... FOR UPDATE) to guarantee
 * atomic increment of the sequence number within a transaction.
 */
@Repository
public interface ReferenceNumberRepository extends JpaRepository<ReferenceNumberSequence, Integer> {

    /**
     * Retrieves the sequence row for the given year with a pessimistic write lock,
     * ensuring exclusive access within the current transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReferenceNumberSequence r WHERE r.year = :year")
    Optional<ReferenceNumberSequence> findByYearForUpdate(@Param("year") int year);
}
