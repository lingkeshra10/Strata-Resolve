package com.strataresolve.ticket.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for a Property Manager to manually link a duplicate ticket to a primary ticket.
 */
public record LinkDuplicateRequest(
        @NotNull(message = "Primary ticket ID is required")
        UUID primaryTicketId,

        @NotNull(message = "Duplicate ticket ID is required")
        UUID duplicateTicketId
) {
}
