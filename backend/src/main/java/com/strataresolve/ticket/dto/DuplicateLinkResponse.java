package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.TicketDuplicateLink;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a duplicate ticket link.
 */
public record DuplicateLinkResponse(
        UUID id,
        UUID primaryTicketId,
        UUID duplicateTicketId,
        UUID linkedBy,
        Instant linkedAt
) {
    /**
     * Creates a DuplicateLinkResponse from a TicketDuplicateLink entity.
     */
    public static DuplicateLinkResponse from(TicketDuplicateLink link) {
        return new DuplicateLinkResponse(
                link.getId(),
                link.getPrimaryTicketId(),
                link.getDuplicateTicketId(),
                link.getLinkedBy(),
                link.getLinkedAt()
        );
    }
}
