package com.strataresolve.ticket.repository;

import com.strataresolve.ticket.domain.TicketDuplicateLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link TicketDuplicateLink} entities.
 */
@Repository
public interface TicketDuplicateLinkRepository extends JpaRepository<TicketDuplicateLink, UUID> {

    /**
     * Finds all duplicate links where the given ticket is the primary ticket.
     */
    List<TicketDuplicateLink> findByPrimaryTicketId(UUID primaryTicketId);

    /**
     * Finds all duplicate links where the given ticket is the duplicate ticket.
     */
    List<TicketDuplicateLink> findByDuplicateTicketId(UUID duplicateTicketId);

    /**
     * Checks if a specific link already exists between primary and duplicate tickets.
     */
    boolean existsByPrimaryTicketIdAndDuplicateTicketId(UUID primaryTicketId, UUID duplicateTicketId);
}
