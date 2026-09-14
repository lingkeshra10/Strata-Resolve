package com.strataresolve.ticket.repository;

import com.strataresolve.ticket.domain.Comment;
import com.strataresolve.ticket.domain.CommentVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Comment} entities.
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Find all comments for a ticket ordered by creation time ascending (chronological).
     */
    List<Comment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    /**
     * Find comments for a ticket filtered by visibility, ordered chronologically.
     */
    List<Comment> findByTicketIdAndVisibilityOrderByCreatedAtAsc(UUID ticketId, CommentVisibility visibility);
}
