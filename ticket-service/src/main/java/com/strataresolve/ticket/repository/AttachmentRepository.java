package com.strataresolve.ticket.repository;

import com.strataresolve.ticket.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Attachment} entities.
 */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Finds all attachments associated with a specific ticket.
     *
     * @param ticketId the ticket ID
     * @return list of attachments for the ticket, ordered by upload time
     */
    List<Attachment> findByTicketIdOrderByUploadedAtAsc(UUID ticketId);
}
