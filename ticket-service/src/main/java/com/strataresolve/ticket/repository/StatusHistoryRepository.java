package com.strataresolve.ticket.repository;

import com.strataresolve.ticket.domain.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link StatusHistory} entities.
 */
@Repository
public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {

    List<StatusHistory> findByTicketIdOrderByChangedAtAsc(UUID ticketId);
}
