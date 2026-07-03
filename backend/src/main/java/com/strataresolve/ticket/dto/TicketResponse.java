package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.SlaStatus;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a ticket.
 */
public record TicketResponse(
        UUID id,
        UUID propertyId,
        UUID submittedBy,
        UUID unitId,
        String referenceNumber,
        String title,
        String description,
        Category category,
        Priority priority,
        TicketStatus status,
        String location,
        Instant acknowledgementDueAt,
        Instant resolutionDueAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        SlaStatus slaStatus,
        boolean duplicateFlag,
        UUID linkedToTicketId,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Creates a TicketResponse from a Ticket entity.
     */
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getPropertyId(),
                ticket.getSubmittedBy(),
                ticket.getUnitId(),
                ticket.getReferenceNumber(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getLocation(),
                ticket.getAcknowledgementDueAt(),
                ticket.getResolutionDueAt(),
                ticket.getAcknowledgedAt(),
                ticket.getResolvedAt(),
                ticket.getSlaStatus(),
                ticket.isDuplicateFlag(),
                ticket.getLinkedToTicketId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
