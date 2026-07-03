package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.AssignmentType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a ticket assignment.
 *
 * @param ticketId   the ticket to assign
 * @param assignedTo the user (technician) or vendor admin user to assign to
 * @param type       the assignment type (TECHNICIAN or VENDOR)
 */
public record CreateAssignmentRequest(
        @NotNull(message = "Ticket ID is required")
        UUID ticketId,

        @NotNull(message = "Assigned user ID is required")
        UUID assignedTo,

        @NotNull(message = "Assignment type is required")
        AssignmentType type
) {
}
