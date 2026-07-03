package com.strataresolve.ticket.repository;

import com.strataresolve.ticket.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for Assignment entities.
 */
@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    /**
     * Find all assignments for a given ticket.
     */
    List<Assignment> findByTicketId(UUID ticketId);

    /**
     * Find all assignments for a given assignee.
     */
    List<Assignment> findByAssignedTo(UUID assignedTo);
}
