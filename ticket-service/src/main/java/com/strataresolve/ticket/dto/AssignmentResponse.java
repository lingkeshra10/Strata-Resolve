package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.Assignment;
import com.strataresolve.ticket.domain.AssignmentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing an assignment.
 */
public record AssignmentResponse(
        UUID id,
        UUID ticketId,
        UUID assignedTo,
        AssignmentType type,
        Instant assignedAt,
        Instant acceptedAt
) {
    /**
     * Creates an AssignmentResponse from an Assignment entity.
     */
    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getTicketId(),
                assignment.getAssignedTo(),
                assignment.getType(),
                assignment.getAssignedAt(),
                assignment.getAcceptedAt()
        );
    }
}
